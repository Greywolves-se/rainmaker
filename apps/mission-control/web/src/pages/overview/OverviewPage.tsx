import {useSnapshot} from '../../data/useSnapshot';
import {CurrentStatusCard} from './CurrentStatusCard';
import {DecisionsCard} from './DecisionsCard';
import {KpiCards} from './KpiCards';
import {LatestDocumentsCard} from './LatestDocumentsCard';
import {LatestReleaseCard} from './LatestReleaseCard';
import {RecentEventsCard} from './RecentEventsCard';
import {RisksPilotsCostCard} from './RisksPilotsCostCard';

/** The owner view: one component per card, all fed from the snapshot. */
export function OverviewPage() {
  const {overview} = useSnapshot();
  return (
    <div className="stack">
      <KpiCards overview={overview} />
      <CurrentStatusCard overview={overview} />
      <div className="grid">
        <DecisionsCard overview={overview} />
        <RisksPilotsCostCard overview={overview} />
      </div>
      <div className="grid">
        <LatestReleaseCard release={overview.release} />
        <RecentEventsCard events={overview.events} />
      </div>
      <LatestDocumentsCard documents={overview.documents} />
    </div>
  );
}
