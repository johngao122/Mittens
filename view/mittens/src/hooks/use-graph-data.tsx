'use client';

import { useState, useEffect, useCallback } from 'react';

interface GraphDataHook {
  data: any | null;
  loading: boolean;
  error: string | null;
  uploadFile: (file: File) => Promise<boolean>;
  refreshData: () => Promise<void>;
  importData: (data: any) => Promise<boolean>;
}

export function useGraphData(): GraphDataHook {
  const [data, setData] = useState<any | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshData = useCallback(async () => {
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
    refreshData();
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