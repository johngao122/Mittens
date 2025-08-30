import { NextRequest, NextResponse } from "next/server";
import { writeFile, mkdir, readFile } from "fs/promises";
import path from "path";
import { broadcast } from "@/lib/sse";

// Global variable to store the latest imported data
let latestGraphData: any = null;

// Ensure this route is always dynamic and runs in Node.js runtime
export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(request: NextRequest) {
    try {
        const data = await request.json();

        // Validate the data structure
        if (!data.graph || !data.graph.nodes || !data.graph.edges) {
            return NextResponse.json(
                {
                    error: "Invalid data format. Expected graph with nodes and edges.",
                },
                { status: 400 }
            );
        }

        // Store the data globally for retrieval
        latestGraphData = data;

        // Notify connected clients via SSE that new data is available
        try {
            broadcast("graph-update", {
                nodeCount: data.graph.nodes.length,
                edgeCount: data.graph.edges.length,
                timestamp: new Date().toISOString(),
            });
        } catch (e) {
            // Non-fatal if broadcasting fails
            console.warn("SSE broadcast failed:", e);
        }

        // Optionally, also save to a file for persistence
        const dataDir = path.join(process.cwd(), "src", "data");
        const filePath = path.join(dataDir, "imported-dependency-graph.json");

        try {
            await mkdir(dataDir, { recursive: true });
            await writeFile(filePath, JSON.stringify(data, null, 2));
        } catch (fileError) {
            console.warn("Could not save to file:", fileError);
            // Continue even if file save fails
        }

        return NextResponse.json({
            message: "Data imported successfully",
            nodeCount: data.graph.nodes.length,
            edgeCount: data.graph.edges.length,
            timestamp: new Date().toISOString(),
        });
    } catch (error) {
        console.error("Error importing data:", error);
        return NextResponse.json(
            { error: "Failed to import data" },
            { status: 500 }
        );
    }
}

export async function GET() {
    try {
        // If in-memory cache is empty, try to load from persisted file
        if (!latestGraphData) {
            try {
                const dataDir = path.join(process.cwd(), "src", "data");
                const filePath = path.join(
                    dataDir,
                    "imported-dependency-graph.json"
                );
                const raw = await readFile(filePath, "utf-8");
                latestGraphData = JSON.parse(raw);
            } catch (_) {
                // Ignore file read errors and fall back to 404
            }
        }

        if (!latestGraphData) {
            return NextResponse.json(
                { error: "No data available. Import data first." },
                { status: 404 }
            );
        }

        return NextResponse.json({
            data: latestGraphData,
            timestamp: new Date().toISOString(),
        });
    } catch (error) {
        console.error("Error retrieving data:", error);
        return NextResponse.json(
            { error: "Failed to retrieve data" },
            { status: 500 }
        );
    }
}

// Helper function to get the latest data (for use by other parts of the app)
function getLatestGraphData() {
    return latestGraphData;
}
