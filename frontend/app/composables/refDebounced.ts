import type { Ref } from 'vue'

/** Minimal debounced mirror of a ref (avoids pulling in @vueuse just for this). */
export function refDebounced<T>(source: Ref<T>, delay = 300): Readonly<Ref<T>> {
  const debounced = ref(source.value) as Ref<T>
  let timer: ReturnType<typeof setTimeout> | undefined
  watch(source, (v) => {
    clearTimeout(timer)
    timer = setTimeout(() => { debounced.value = v }, delay)
  })
  return readonly(debounced) as Readonly<Ref<T>>
}
