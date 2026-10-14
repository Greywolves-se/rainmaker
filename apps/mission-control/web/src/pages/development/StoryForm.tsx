import {useState, type FormEvent} from 'react';
import {useTranslation} from 'react-i18next';
import type {StoryStatus} from '../../data/types';
import {STORY_STATUSES, type StoryDraft} from './stories';

const EMPTY_DRAFT: StoryDraft = {title: '', owner: '', status: 'todo'};

interface StoryFormProps {
  onAdd: (draft: StoryDraft) => void;
  onCancel: () => void;
}

export function StoryForm({onAdd, onCancel}: StoryFormProps) {
  const {t} = useTranslation();
  const [draft, setDraft] = useState<StoryDraft>(EMPTY_DRAFT);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.title.trim()) {
      return;
    }
    onAdd(draft);
    setDraft(EMPTY_DRAFT);
  }

  return (
    <form className="story-form" onSubmit={submit}>
      <div className="field story-form__title">
        <label htmlFor="story-title" className="field__label">
          {t('development.storyLabel')}
        </label>
        <input
          id="story-title"
          type="text"
          className="field__input"
          required
          value={draft.title}
          placeholder={t('development.storyPlaceholder')}
          onChange={event => setDraft({...draft, title: event.target.value})}
        />
      </div>
      <div className="field">
        <label htmlFor="story-owner" className="field__label">
          {t('development.ownerLabel')}
        </label>
        <input
          id="story-owner"
          type="text"
          className="field__input story-form__owner"
          value={draft.owner}
          placeholder={t('development.ownerPlaceholder')}
          onChange={event => setDraft({...draft, owner: event.target.value})}
        />
      </div>
      <div className="field">
        <label htmlFor="story-status" className="field__label">
          {t('development.statusLabel')}
        </label>
        <select
          id="story-status"
          className="field__input"
          value={draft.status}
          onChange={event =>
            setDraft({...draft, status: event.target.value as StoryStatus})
          }
        >
          {STORY_STATUSES.map(status => (
            <option key={status} value={status}>
              {t(`storyStatus.${status}`)}
            </option>
          ))}
        </select>
      </div>
      <button type="submit" className="button button--dark">
        {t('development.add')}
      </button>
      <button
        type="button"
        className="button button--secondary"
        onClick={onCancel}
      >
        {t('development.cancel')}
      </button>
      <span className="story-form__hint">{t('development.localOnlyHint')}</span>
    </form>
  );
}
