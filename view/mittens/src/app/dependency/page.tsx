import { DependencyNetwork } from "@/components/chart-blocks";
import Container from "@/components/container";
import ClientOnly from "@/components/client-only";

export default function DependencyPage() {
  return (
    <div>
      <Container className="py-8">
        <ClientOnly fallback={<div className="h-96 w-full bg-slate-800 rounded-lg animate-pulse"></div>}>
          <DependencyNetwork />
        </ClientOnly>
      </Container>
    </div>
  );
}
