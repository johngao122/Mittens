'use client';

import Container from "@/components/container";
import dynamic from 'next/dynamic';

// Import D3Network with no SSR to prevent hydration issues
const DependencyNetwork = dynamic(
  () => import("@/components/chart-blocks").then(mod => ({ default: mod.DependencyNetwork })),
  { 
    ssr: false,
    loading: () => <div className="h-96 w-full bg-slate-800 rounded-lg animate-pulse"></div>
  }
);

export default function TicketPage() {
  return (
    <div>
      <Container className="py-8">
        <DependencyNetwork />
      </Container>
    </div>
  );
}
