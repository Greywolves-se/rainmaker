import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Markdown} from './markdown';
import {START_HERE_URL, fetchStartHere} from './startHereClient';

type LoadState =
  | {status: 'loading'}
  | {status: 'missing'}
  | {status: 'error'; message: string}
  | {status: 'ready'; report: string};

/** The START HERE report (#25), rendered, with Download and Copy for Claude Code. */
export function StartHerePage() {
  const {t} = useTranslation();
  const [state, setState] = useState<LoadState>({status: 'loading'});

  useEffect(() => {
    let cancelled = false;
    fetchStartHere().then(
      report => {
        if (!cancelled) {
          setState(
            report === null ? {status: 'missing'} : {status: 'ready', report}
          );
        }
      },
      (error: unknown) => {
        console.error(
          'Mission Control: could not load the START HERE report',
          error
        );
        if (!cancelled) {
          setState({status: 'error', message: String(error)});
        }
      }
    );
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === 'loading') {
    return <p className="item__meta">{t('startHere.loading')}</p>;
  }
  if (state.status === 'missing') {
    return (
      <section className="card">
        <p>{t('startHere.missing')}</p>
      </section>
    );
  }
  if (state.status === 'error') {
    return (
      <section className="card" role="alert">
        <p>{t('startHere.error')}</p>
        <pre className="start-here__error">{state.message}</pre>
      </section>
    );
  }
  return (
    <section className="card">
      <ReportActions report={state.report} />
      <Markdown source={state.report} />
    </section>
  );
}

function ReportActions({report}: {report: string}) {
  const {t} = useTranslation();
  const [copied, setCopied] = useState<'idle' | 'copied' | 'failed'>('idle');

  async function copy() {
    try {
      await navigator.clipboard.writeText(report);
      setCopied('copied');
    } catch {
      // Outside https/localhost navigator.clipboard is undefined, so this also catches the
      // TypeError; the browser can also refuse. Either way, Download .md still works.
      setCopied('failed');
    }
  }

  return (
    <div className="start-here__actions">
      {/* Same file as on the page, so the download is byte-for-byte what the collector wrote. */}
      <a
        className="button button--primary"
        href={START_HERE_URL}
        download="start-here.md"
      >
        {t('startHere.download')}
      </a>
      <button type="button" className="button button--secondary" onClick={copy}>
        {t('startHere.copy')}
      </button>
      <span className="item__meta" role="status">
        {copied === 'copied' && t('startHere.copied')}
        {copied === 'failed' && t('startHere.copyFailed')}
      </span>
    </div>
  );
}
