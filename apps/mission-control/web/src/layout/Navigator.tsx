import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {SparkleIcon} from '../components/icons';
import type {AiPage, NavigatorAi} from '../data/types';
import {formatTimestamp} from '../i18n/format';
import type {PageKey} from './pages';
import {STALE_AFTER_MS} from './SnapshotBanners';
import {useNow} from './useNow';

const AI_PAGES: readonly PageKey[] = ['overview', 'development', 'sources'];

function isAiPage(page: PageKey): page is AiPage {
  return AI_PAGES.includes(page);
}

/**
 * The Navigator panel: the AI summary, deviations and suggestions for the page you are on
 * (#116, ADR 0002), and the Ask box, which stays disabled until #117.
 */
export function Navigator({
  navigator,
  projectName,
  page,
  demo,
}: {
  navigator: NavigatorAi;
  projectName: string;
  page: PageKey;
  /** Demo data is fictional and old on purpose: no stale hint, like the app's banner. */
  demo: boolean;
}) {
  const {t} = useTranslation();
  return (
    <aside className="navigator" aria-label={t('navigator.label')}>
      <div className="navigator__header">
        <span className="navigator__icon">
          <SparkleIcon />
        </span>
        <div className="item">
          <h2 className="navigator__title">{t('navigator.title')}</h2>
          <span className="item__meta">{projectName}</span>
        </div>
      </div>

      {/* Hidden when the AI step is off, before #115, and on pages without AI text. */}
      {navigator.status === 'on' && isAiPage(page) && (
        <AiText navigator={navigator} page={page} demo={demo} />
      )}

      <div className="navigator__section">
        <h3>{t('navigator.ask')}</h3>
        <div className="ask-row">
          <label htmlFor="mc-ask" className="sr-only">
            {t('navigator.askOwnLabel')}
          </label>
          <input
            id="mc-ask"
            type="text"
            className="ask-row__input"
            placeholder={t('navigator.askOwnPlaceholder')}
            title={t('navigator.comingLater')}
            disabled
          />
          <button type="button" className="button button--primary" disabled>
            {t('navigator.askButton')}
          </button>
        </div>
        <p className="item__meta">{t('navigator.comingLater')}</p>
      </div>
    </aside>
  );
}

/**
 * The page's AI text in the reader's language. A model wrote it, so every part is a plain React
 * text node: HTML or Markdown in it is shown as written, never rendered.
 */
function AiText({
  navigator,
  page,
  demo,
}: {
  navigator: Extract<NavigatorAi, {status: 'on'}>;
  page: AiPage;
  demo: boolean;
}) {
  const {t, i18n} = useTranslation();
  const now = useNow();
  const pageText = navigator.text?.pages[page];

  if (!navigator.text || !pageText) {
    return (
      <div className="navigator__summary">
        <p>{t('navigator.noSummary')}</p>
        {navigator.reason && <p className="item__meta">{navigator.reason}</p>}
      </div>
    );
  }

  const insight = i18n.language === 'en' ? pageText.en : pageText.sv;
  const {model, generatedAt} = navigator.text;
  const stale = !demo && now - Date.parse(generatedAt) > STALE_AFTER_MS;
  const generated = formatTimestamp(generatedAt, i18n.language);
  // The day only when it is not today (in Stockholm), so yesterday's text never looks new.
  const isToday =
    generated.day ===
    formatTimestamp(new Date(now).toISOString(), i18n.language).day;
  return (
    <div className="navigator__summary">
      <p>{insight.summary}</p>
      <AiList
        titleKey="navigator.deviations"
        items={insight.deviations}
        warning
      />
      <AiList titleKey="navigator.suggestions" items={insight.suggestions} />
      <p className="item__meta">
        {isToday
          ? t('navigator.aiLabel', {model, time: generated.time})
          : t('navigator.aiLabelWithDate', {
              model,
              day: generated.day,
              time: generated.time,
            })}{' '}
        <a href="#main-content">{t('navigator.basedOn')}</a>
      </p>
      {stale && <p className="warning-banner">{t('snapshot.stale')}</p>}
    </div>
  );
}

function AiList({
  titleKey,
  items,
  warning = false,
}: {
  titleKey: string;
  items: string[];
  warning?: boolean;
}) {
  const {t} = useTranslation();
  const headingId = useId();
  if (items.length === 0) {
    return null;
  }
  return (
    <>
      <h3 id={headingId} className="navigator__list-title">
        {t(titleKey)}
      </h3>
      <ul
        aria-labelledby={headingId}
        className={
          warning
            ? 'navigator__list navigator__list--warning'
            : 'navigator__list'
        }
      >
        {items.map((item, index) => (
          <li key={index}>{item}</li>
        ))}
      </ul>
    </>
  );
}
