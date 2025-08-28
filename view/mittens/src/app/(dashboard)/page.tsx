import {
  AverageTicketsCreated,
  Conversions,
  CustomerSatisfication,
  Metrics,
  TicketByChannels,
} from "@/components/chart-blocks";
import Container from "@/components/container";
import ClientOnly from "@/components/client-only";

export default function Home() {
  return (
    <div>
      <Metrics />
      <div className="grid grid-cols-1 divide-y border-b border-border laptop:grid-cols-3 laptop:divide-x laptop:divide-y-0 laptop:divide-border">
        <Container className="py-4 laptop:col-span-2">
          <ClientOnly fallback={<div>Loading chart...</div>}>
            <AverageTicketsCreated />
          </ClientOnly>
        </Container>
        <Container className="py-4 laptop:col-span-1">
          <ClientOnly fallback={<div>Loading chart...</div>}>
            <Conversions />
          </ClientOnly>
        </Container>
      </div>
      <div className="grid grid-cols-1 divide-y border-b border-border laptop:grid-cols-2 laptop:divide-x laptop:divide-y-0 laptop:divide-border">
        <Container className="py-4 laptop:col-span-1">
          <ClientOnly fallback={<div>Loading chart...</div>}>
            <TicketByChannels />
          </ClientOnly>
        </Container>
        <Container className="py-4 laptop:col-span-1">
          <ClientOnly fallback={<div>Loading chart...</div>}>
            <CustomerSatisfication />
          </ClientOnly>
        </Container>
      </div>
    </div>
  );
}
