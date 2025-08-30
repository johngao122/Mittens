import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData();
    const file = formData.get('file') as File;

    if (!file) {
      return NextResponse.json(
        { error: 'No file provided' },
        { status: 400 }
      );
    }

    // Check if it's a JSON file
    if (!file.name.endsWith('.json')) {
      return NextResponse.json(
        { error: 'Only JSON files are allowed' },
        { status: 400 }
      );
    }

    // Read and parse the file
    const text = await file.text();
    let data;
    
    try {
      data = JSON.parse(text);
    } catch (parseError) {
      return NextResponse.json(
        { error: 'Invalid JSON file' },
        { status: 400 }
      );
    }

    // Validate the data structure
    if (!data.graph || !data.graph.nodes || !data.graph.edges) {
      return NextResponse.json(
        { error: 'Invalid data format. Expected graph with nodes and edges.' },
        { status: 400 }
      );
    }

    // Forward to the import-data endpoint
    const importResponse = await fetch(new URL('/api/import-data', request.url), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });

    if (!importResponse.ok) {
      throw new Error('Failed to import data');
    }

    const result = await importResponse.json();

    return NextResponse.json({
      message: 'File uploaded and data imported successfully',
      filename: file.name,
      size: file.size,
      ...result
    });

  } catch (error) {
    console.error('Error uploading file:', error);
    return NextResponse.json(
      { error: 'Failed to upload file' },
      { status: 500 }
    );
  }
}