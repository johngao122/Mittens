import { NextRequest, NextResponse } from 'next/server';
import { spawn } from 'child_process';
import { readFile } from 'fs/promises';
import path from 'path';

// Runs `./gradlew shadowJarWithKnit` inside the sample project, then
// reads build/knit.json and forwards it to /api/import-data to update state/SSE.
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  try {
    // Resolve project paths
    // CWD for Next API is the view/mittens folder; go up to repo root, then sample_project
    const repoRoot = path.join(process.cwd(), '..', '..');
    const sampleProjectDir = path.join(repoRoot, 'sample_project');
    const gradlewPath = path.join(
      sampleProjectDir,
      process.platform === 'win32' ? 'gradlew.bat' : 'gradlew'
    );

    // Run the Gradle task
    const startedAt = Date.now();
    const result = await runGradleTask(gradlewPath, ['shadowJarWithKnit'], sampleProjectDir);

    if (!result.success) {
      return NextResponse.json(
        { error: 'Gradle task failed', details: result.errorOutput || result.output },
        { status: 500 }
      );
    }

    // Read the generated knit.json
    const knitJsonPath = path.join(sampleProjectDir, 'build', 'knit.json');
    const raw = await readFile(knitJsonPath, 'utf-8');
    const data = JSON.parse(raw);

    // Forward to import-data endpoint so the in-memory cache + SSE updates
    const importResponse = await fetch(new URL('/api/import-data', request.url), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });

    if (!importResponse.ok) {
      const txt = await importResponse.text();
      return NextResponse.json(
        { error: 'Importing built data failed', details: txt },
        { status: 500 }
      );
    }

    const importResult = await importResponse.json();
    const durationMs = Date.now() - startedAt;

    return NextResponse.json({
      message: 'Build and import successful',
      durationMs,
      import: importResult,
      data, // Return the data directly so clients can update immediately
    });
  } catch (err: any) {
    return NextResponse.json(
      { error: 'Refresh failed', details: err?.message || String(err) },
      { status: 500 }
    );
  }
}

async function runGradleTask(
  gradlewPath: string,
  args: string[],
  cwd: string
): Promise<{ success: boolean; output: string; errorOutput: string }> {
  const trySpawn = (cmd: string, argv: string[]) =>
    new Promise<{ code: number | null; stdout: string; stderr: string }>((resolve) => {
      const child = spawn(cmd, argv, { cwd, shell: false });
      let stdout = '';
      let stderr = '';
      child.stdout.on('data', (d) => (stdout += d.toString()));
      child.stderr.on('data', (d) => (stderr += d.toString()));
      child.on('close', (code) => resolve({ code, stdout, stderr }));
      child.on('error', () => resolve({ code: -1, stdout, stderr }));
    });

  // First try executing gradlew directly
  let res = await trySpawn(gradlewPath, args);
  if (res.code !== 0) {
    // Fallback to sh wrapper (helps if exec bit isn’t set)
    res = await trySpawn('sh', [gradlewPath, ...args]);
  }
  return { success: res.code === 0, output: res.stdout, errorOutput: res.stderr };
}
