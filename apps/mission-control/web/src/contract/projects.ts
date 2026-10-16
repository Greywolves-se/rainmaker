/**
 * TypeScript form of `projects.json` (apps/mission-control/contract/projects.schema.json, #99):
 * the projects that have a snapshot. Each project's snapshot is `<id>/snapshot.json` next to it.
 */
import {z} from 'zod';

export const projectListSchema = z.object({
  schemaVersion: z.literal(1),
  projects: z
    .array(
      z.object({
        id: z.string().regex(/^[a-z][a-z0-9-]*$/),
        name: z.string().min(1),
      })
    )
    .min(1),
});

export type ProjectList = z.infer<typeof projectListSchema>;
export type ProjectEntry = ProjectList['projects'][number];

/**
 * Checks untrusted JSON (e.g. a fetched projects.json) against the contract.
 * Throws a `ZodError` that names the failing fields when it does not match.
 */
export function parseProjectList(json: unknown): ProjectList {
  return projectListSchema.parse(json);
}
