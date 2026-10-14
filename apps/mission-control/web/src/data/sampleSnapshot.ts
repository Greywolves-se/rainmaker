import type {Snapshot} from './types';

/**
 * FICTIONAL sample data copied from the design mockup. It is not real project
 * status. It is replaced by the collector's snapshot once the contract (#10) exists.
 */
export const SAMPLE_SNAPSHOT: Snapshot = {
  generatedAt: '2026-10-14T08:24:00Z',
  projects: [
    {name: 'Rainmaker', health: 'green'},
    {name: 'Friction Intelligence', health: 'green'},
    {name: 'Mission Control', health: 'yellow'},
  ],
  sourceSummary: {synced: 5, total: 6, note: 'AWS awaiting permissions'},
  overview: {
    project: {status: 'onTrack', sprint: 'Sprint 2', percentDone: 75},
    operations: {status: 'stable', note: 'No critical incidents'},
    business: {status: 'growing', note: 'Traffic +12% this week'},
    pilots: {active: 3, note: '2 running · 1 starts in 6 days'},
    done: [
      {title: 'Release v0.8.1 in production', meta: 'GitHub · yesterday'},
      {title: 'Architecture v2.1', meta: 'Drive · Simon · 1 hr ago'},
      {title: 'Pilot plan Q1', meta: 'Drive · Peter · 3 hrs ago'},
      {title: 'PR #233 Update documentation', meta: 'GitHub · merged'},
    ],
    inProgress: [
      {title: 'Sprint 2 – 75% done', meta: 'GitHub · 7 stories'},
      {title: 'Pilot – Retail customer', meta: 'Friction · started 12 Jan'},
      {title: 'Pilot – Industrial customer', meta: 'Friction · started 7 Jan'},
      {title: 'Material for Friday customer meeting', meta: 'Drive · Caroline'},
    ],
    blocked: [
      {title: 'AWS health is missing an IAM role', meta: 'Slack #tech · Erik'},
      {
        title: 'Pilot data from Service company missing',
        meta: 'Slack #friction · Caroline',
      },
    ],
    decisionMakers: ['Peter', 'Staffan'],
    decisions: [
      {
        initial: 'P',
        title: 'Approve Service company pilot start 20 Oct',
        meta: 'Peter · Friction Intelligence · by Fri',
      },
      {
        initial: 'P',
        title: 'Which customer do we show in Friday’s demo?',
        meta: 'Peter · Slack #rainmaker',
      },
      {
        initial: 'S',
        title: 'Budget for AWS staging',
        meta: 'Staffan · cost rises with the new environment',
      },
    ],
    pilotList: [
      {
        title: 'Pilot – Retail customer',
        meta: 'Result +18%',
        status: 'running',
      },
      {
        title: 'Pilot – Industrial customer',
        meta: 'Result +12%',
        status: 'running',
      },
      {
        title: 'Pilot – Service company',
        meta: 'Starts 20 Oct',
        status: 'atRisk',
      },
    ],
    risk: {
      title: 'Service company pilot start',
      text: 'Two open blockers could push back the 20 Oct start. Pilot data is missing and AWS monitoring is not in place.',
    },
    awsCost: {month: '2026-10', amountSek: null},
    release: {
      version: 'v0.8.1',
      badge: 'Production · yesterday',
      notes:
        'Campaign view, pilot overview and a fix for the login error. Tests passing on main.',
    },
    events: [
      {
        source: 'GH',
        title: 'PR #234 ready for review',
        meta: 'GitHub · 12 min ago',
      },
      {
        source: 'GD',
        title: 'New architecture file added',
        meta: 'Drive · Simon · 1 hr ago',
      },
      {
        source: 'SL',
        title: 'Discussion about pilot start',
        meta: 'Slack · 2 hrs ago',
      },
      {
        source: 'AWS',
        title: 'CPU usage back to normal',
        meta: 'AWS · 3 hrs ago',
      },
      {
        source: 'FI',
        title: 'New pilot activated',
        meta: 'Friction Intelligence · 4 hrs ago',
      },
    ],
  },
  development: {
    sprint: {name: 'Sprint 2', dates: '12–16 Oct'},
    stories: [
      {
        id: 'MC-01',
        title: 'Fetch issues, PRs and releases from GitHub',
        owner: 'Erik',
        status: 'done',
      },
      {
        id: 'MC-02',
        title: 'Read documents from GreyWolves Google Drive',
        owner: 'Caroline',
        status: 'done',
      },
      {
        id: 'MC-03',
        title: 'Capture decisions and blockers from Slack',
        owner: 'Erik',
        status: 'inProgress',
      },
      {
        id: 'MC-04',
        title: 'Normalize data to project, owner and status',
        owner: 'Simon',
        status: 'inProgress',
      },
      {
        id: 'MC-05',
        title: 'Overview: done, in progress and blocked',
        owner: 'Caroline',
        status: 'inProgress',
      },
      {
        id: 'MC-06',
        title: 'Show system health from AWS',
        owner: 'Erik',
        status: 'blocked',
      },
      {
        id: 'MC-07',
        title: 'Navigator: AI summary of the current status',
        owner: 'Simon',
        status: 'todo',
      },
    ],
    pullRequests: [
      {
        number: '#234',
        title: 'Campaign view with per-channel filter',
        status: 'review',
        meta: 'Erik · tests passing · 12 min ago',
      },
      {
        number: '#236',
        title: 'Normalize Slack events',
        status: 'review',
        meta: 'Simon · 1 test failing · 1 hr ago',
      },
      {
        number: '#237',
        title: 'Pilot overview for Friction',
        status: 'review',
        meta: 'Caroline · tests running · 2 hrs ago',
      },
      {
        number: '#233',
        title: 'Update documentation',
        status: 'merged',
        meta: 'Erik · 3 hrs ago',
      },
    ],
    build: {
      branch: 'main',
      number: 128,
      age: '6 min ago',
      duration: '4 min 12 s',
      unitTests: 'Passing',
      lint: 'Passing',
      staging: 'v0.8.1',
      steps: ['install · 38 s', 'test · 2 min 41 s', 'deploy staging · 53 s'],
    },
    techDecisions: [
      {
        title: 'One shared data model in Postgres (RDS)',
        meta: 'Decision · Simon · Architecture v2.1',
        status: 'decided',
      },
      {
        title: 'Slack via Events API instead of polling',
        meta: 'Proposal · Erik',
        status: 'proposed',
      },
      {
        title: 'IAM role for reading CloudWatch',
        meta: 'Dependency for MC-06',
        status: 'missing',
      },
      {
        title: 'API key for Rainmaker traffic',
        meta: 'Dependency for the business status card',
        status: 'done',
      },
    ],
    questionAudience: 'Simon / supervisors',
    questions: [
      {
        status: 'blocker',
        audience: 'for Simon',
        title: 'Who can create the IAM role for CloudWatch?',
        meta: 'Erik · Slack #tech · 08:47',
      },
      {
        status: 'question',
        audience: 'for supervisors',
        title: 'How do we link pilots to customers in Rainmaker?',
        meta: 'Caroline · yesterday',
      },
      {
        status: 'question',
        audience: 'for Simon',
        title: 'Should Navigator be allowed to read private Slack channels?',
        meta: 'Erik · yesterday',
      },
    ],
    links: [
      {title: 'Production', meta: 'Rainmaker · v0.8.1'},
      {title: 'Staging', meta: 'Latest build #128'},
      {title: 'Logs', meta: 'AWS CloudWatch'},
      {title: 'Repo', meta: 'GitHub · rainmaker'},
      {title: 'Architecture v2.1', meta: 'Google Drive'},
      {title: 'Technical spec', meta: 'Google Drive · PDF'},
    ],
  },
  sources: {
    cards: [
      {
        code: 'GD',
        name: 'Google Drive',
        account: 'GreyWolves accounts',
        role: 'What we have decided and documented',
        tags: [
          'Documents',
          'Specifications',
          'Reports',
          'Decisions and background',
        ],
        scope: 'Folders: Rainmaker, Friction, Mission Control',
        mapsTo: 'Decisions, documents, owner (file owner)',
        updates: 'Every 5 minutes',
        status: 'synced',
        lastSync: 'Last sync 2 min ago',
      },
      {
        code: 'GH',
        name: 'GitHub',
        account: 'Private accounts, not GreyWolves',
        role: 'What we are actually building',
        tags: [
          'Code',
          'Issues',
          'Pull requests',
          'Releases',
          'Development status',
        ],
        scope: 'Repos: rainmaker, friction-intelligence, mission-control',
        mapsTo: 'Activity, status, owner (assignee)',
        updates: 'Real time via webhooks',
        status: 'synced',
        lastSync: 'Last sync 1 min ago',
      },
      {
        code: 'SL',
        name: 'Slack',
        account: 'Team channels',
        role: 'What the team is talking about',
        tags: [
          'Ongoing dialogue',
          'Key decisions',
          'Problems and blockers',
          'Team status',
        ],
        scope: '#rainmaker, #friction-intelligence, #tech, #leadership',
        mapsTo: 'Decisions, blockers, status',
        updates: 'Real time via Events API',
        status: 'synced',
        lastSync: 'Real time',
      },
      {
        code: 'AWS',
        name: 'AWS',
        account: 'Operations and systems',
        role: 'Whether the machinery works',
        tags: [
          'Operational status',
          'System health',
          'Errors and incidents',
          'Activity and performance',
        ],
        scope: 'ECS, RDS, S3 via CloudWatch',
        mapsTo: 'Operational status, incidents',
        updates: 'Every minute',
        status: 'partial',
        lastSync: 'CloudWatch awaiting IAM role',
      },
      {
        code: 'RM',
        name: 'Rainmaker',
        account: 'Business',
        role: 'What the business produces',
        tags: ['Customers', 'Traffic', 'Campaigns', 'Revenue', 'Results'],
        scope: 'Customers, traffic, campaigns, revenue',
        mapsTo: 'KPIs, business status',
        updates: 'Every hour',
        status: 'synced',
        lastSync: 'Last sync 5 min ago',
      },
      {
        code: 'FI',
        name: 'Friction Intelligence',
        account: 'Pilots',
        role: 'What the business produces',
        tags: [
          'Pilots',
          'Analyses',
          'Results',
          'Prioritized actions',
          'Status',
        ],
        scope: 'Pilots, analyses, actions',
        mapsTo: 'Pilot status, actions, owner',
        updates: 'Every 15 minutes',
        status: 'synced',
        lastSync: 'Last sync 10 min ago',
      },
    ],
    mapping: [
      {
        concept: 'Project',
        from: 'GitHub repo, Drive folder, Slack channel',
        example: '#rainmaker → Rainmaker',
      },
      {
        concept: 'Activity',
        from: 'PRs, issues, releases, new files, messages',
        example: 'PR #234 ready for review',
      },
      {
        concept: 'Owner',
        from: 'Assignee in GitHub, file owner in Drive, sender in Slack',
        example: 'Simon',
      },
      {
        concept: 'Status',
        from: 'GitHub labels, pilot status, AWS health',
        example: 'In progress / Blocked',
      },
      {
        concept: 'Decision',
        from: 'Slack messages tagged decision, decision docs in Drive',
        example: 'Decision: go ahead with pilot 2',
      },
      {
        concept: 'Blocker',
        from: 'Slack threads tagged blocker, GitHub label blocked',
        example: 'IAM role missing',
      },
    ],
    syncLog: [
      {
        time: '10:22',
        source: 'GH',
        text: '4 new events (PR #234, #236, #237)',
        status: 'ok',
      },
      {
        time: '10:20',
        source: 'GD',
        text: '2 new files: Architecture v2.1, Customer meeting material',
        status: 'ok',
      },
      {
        time: '10:18',
        source: 'SL',
        text: '1 decision and 1 blocker found in #tech',
        status: 'ok',
      },
      {
        time: '10:15',
        source: 'AWS',
        text: 'CloudWatch: access denied, IAM role missing',
        status: 'error',
      },
      {
        time: '10:10',
        source: 'FI',
        text: 'Service company pilot created, starts 20 Oct',
        status: 'ok',
      },
    ],
  },
  navigator: {
    summaryTime: '10:20',
    summary:
      'Rainmaker: green. Sprint 2 is 75% done. Two open blockers. New architecture file from Simon in Drive. Three PRs awaiting review. Pilot starts in 6 days. No critical outage.',
    projects: [
      {
        title: 'Rainmaker',
        health: 'green',
        text: 'Sprint 2 is 75% done. Three PRs awaiting review.',
      },
      {
        title: 'Friction Intelligence',
        health: 'green',
        text: 'Two pilots running. New pilot starts in 6 days.',
      },
      {
        title: 'Operations (AWS)',
        health: 'green',
        text: 'All systems stable. No critical outage.',
      },
      {
        title: 'Key notes',
        health: 'yellow',
        text: 'Two open blockers. New architecture file from Simon in Drive.',
      },
    ],
    nextSteps: [
      'Get the three PRs reviewed (Simon).',
      'Fix the IAM role so AWS health shows up.',
      'Prepare material for the 20 Oct pilot start.',
      'Peter: approve the customer for Friday’s demo.',
    ],
    questions: [
      {
        question: 'What is happening now?',
        answer:
          'Sprint 2 is underway (75%). Three PRs await review, and the Retail and Industrial pilots are running.',
      },
      {
        question: 'What is done?',
        answer:
          'Release v0.8.1 is in production, Architecture v2.1 and Pilot plan Q1 are in Drive, PR #233 is merged.',
      },
      {
        question: 'What are the next steps?',
        answer:
          'Review PRs #234, #236 and #237, fix the AWS IAM role and prepare the pilot start on 20 Oct.',
      },
      {
        question: 'Any blockers?',
        answer:
          'Yes, two: AWS health is missing an IAM role (Erik) and pilot data from the Service company is missing (Caroline).',
      },
      {
        question: 'Who is responsible?',
        answer:
          'Simon: architecture and review. Erik: integrations. Caroline: pilots and overview. Peter and Staffan: decisions.',
      },
      {
        question: 'How are the systems?',
        answer:
          'All systems stable. CPU back to normal after a spike this morning. No 5xx errors in the last 24 hours.',
      },
      {
        question: 'How is the business doing?',
        answer:
          'Rainmaker is growing: traffic +12% and 24 active customers. The Retail pilot shows +18% in results.',
      },
    ],
  },
};
