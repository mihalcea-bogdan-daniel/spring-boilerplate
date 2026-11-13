import type { AccessDto } from '~/types/api'

/**
 * What the current user may do with one owned resource, and who has access to it. For hiding buttons only:
 * the server checks every request again.
 *
 *   const { can } = useAccess('project', () => project.value?.id)
 *   <Button v-if="can.write" … />
 */
export function useAccess(type: string, id: MaybeRefOrGetter<string | null | undefined>) {
  const { data, pending, error, refresh } = useApi<AccessDto>(
    () => `/access/${type}/${toValue(id) ?? '_'}`,
    { server: false, immediate: !!toValue(id), watch: [() => toValue(id)] },
  )
  const can = computed<Record<string, boolean>>(() => data.value?.can ?? {})
  return { access: data, can, pending, error, refresh }
}
