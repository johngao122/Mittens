'use client';

import { useState, useEffect, useCallback } from 'react';

interface GraphDataHook {
  data: any | null;
  loading: boolean;
  error: string | null;
  uploadFile: (file: File) => Promise<boolean>;
  refreshData: (opts?: { fromSse?: boolean }) => Promise<void>;
  importData: (data: any) => Promise<boolean>;
}

export function useGraphData(): GraphDataHook {
  const [data, setData] = useState<any | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchLatest = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch('/api/import-data');
      if (response.ok) {
        const result = await response.json();
        setData(result.data);
      } else if (response.status === 404) {
        // No data available - this is OK
        setData(null);
      } else {
        throw new Error('Failed to fetch data');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshData = useCallback(async (opts?: { fromSse?: boolean }) => {
    // If called from SSE, only fetch latest data; do not trigger builds
    if (opts?.fromSse) {
      await fetchLatest();
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const bridge = (typeof window !== 'undefined' && (window as any).mittensBridge) || null;
      if (bridge?.refresh) {
        // Ask IntelliJ plugin to build and push data; SSE should update us.
        try { bridge.refresh(); } catch (_) {}
        // Safety fallback: poll once after a short delay
        setTimeout(() => { fetchLatest(); }, 1500);
        return;
      }

      // Fallback for browser-only use: run local server-side build route
      const refreshResp = await fetch('/api/refresh', { method: 'POST' });
      if (refreshResp.ok) {
        const result = await refreshResp.json();
        if (result?.data) {
          setData(result.data);
          return;
        }
      }

      // As a last resort, just fetch whatever is available
      await fetchLatest();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, [fetchLatest]);

  const uploadFile = useCallback(async (file: File): Promise<boolean> => {
    try {
      setLoading(true);
      setError(null);

      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch('/api/upload-file', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        const errorResult = await response.json();
        throw new Error(errorResult.error || 'Upload failed');
      }

      const result = await response.json();
      console.log('Upload successful:', result);

      // Refresh data after successful upload
      await refreshData();
      return true;

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
      return false;
    } finally {
      setLoading(false);
    }
  }, [refreshData]);

  const importData = useCallback(async (graphData: any): Promise<boolean> => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch('/api/import-data', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(graphData),
      });

      if (!response.ok) {
        const errorResult = await response.json();
        throw new Error(errorResult.error || 'Import failed');
      }

      const result = await response.json();
      console.log('Import successful:', result);

      // Refresh data after successful import
      await refreshData();
      return true;

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Import failed');
      return false;
    } finally {
      setLoading(false);
    }
  }, [refreshData]);

  // Load data on mount
  useEffect(() => {
    fetchLatest();
  }, [fetchLatest]);

  // Live updates via Server-Sent Events (SSE)
  useEffect(() => {
    let es: EventSource | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

    const connect = () => {
      // Ensure previous is closed
      if (es) {
        try { es.close(); } catch (_) {}
        es = null;
      }
      es = new EventSource('/api/stream');

      // On any message, refresh data
      es.onmessage = () => {
        refreshData({ fromSse: true });
      };

      // Specifically handle graph-update events (more explicit)
      es.addEventListener('graph-update', () => {
        refreshData({ fromSse: true });
      });

      // Reconnect on error after small delay
      es.onerror = () => {
        if (es) { try { es.close(); } catch (_) {} }
        es = null;
        if (reconnectTimer) clearTimeout(reconnectTimer);
        reconnectTimer = setTimeout(connect, 2000);
      };
    };

    connect();

    return () => {
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (es) { try { es.close(); } catch (_) {} }
      es = null;
    };
  }, [refreshData]);

  return {
    data,
    loading,
    error,
    uploadFile,
    refreshData,
    importData,
  };
}
