/**
 * TypeScript form of `projects.json` (apps/mission-control/contract/projects.schema.json, #99):
 * the projects that have a snapshot. Each project's snapshot is `<id>/snapshot.json` next to it.
 * Like the snapshot, unknown fields are dropped instead of rejected, so a newer collector never
 * breaks an older dashboard.
 */
import {z} from 'zod';

/** Same pattern and limit as `id` in the JSON Schema; the contract test compares them. */
export const projectIdPattern = /^[a-z][a-z0-9-]*$/;
export const projectIdMaxLength = 40;

export const projectListSchema = z.object({
  schemaVersion: z.literal(1),
  projects: z
    .array(
      z.object({
        id: z.string().regex(projectIdPattern).max(projectIdMaxLength),
        name: z.string().min(1),
      })
    )
    .min(1)
    .superRefine((projects, context) => {
      // Ids are keys and routes in the web app; JSON Schema cannot require them to be unique.
      const seen = new Set<string>();
      projects.forEach((project, index) => {
        if (seen.has(project.id)) {
          context.addIssue({
            code: 'custom',
            message: `id ${project.id} is used twice`,
            path: [index, 'id'],
          });
        }
        seen.add(project.id);
      });
    }),
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
