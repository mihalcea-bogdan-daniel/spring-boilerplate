/**
 * Texts that PrimeVue renders itself (empty filters, password strength, paginator labels...).
 * English is PrimeVue's built-in default; add an entry here for every other locale in nuxt.config.ts.
 */
export const primeVueLocales: Record<string, Record<string, unknown>> = {
  ro: {
    accept: 'Da',
    reject: 'Nu',
    choose: 'Alege',
    upload: 'Încarcă',
    cancel: 'Anulează',
    clear: 'Golește',
    apply: 'Aplică',
    noFilter: 'Fără filtru',
    weak: 'Slabă',
    medium: 'Medie',
    strong: 'Puternică',
    passwordPrompt: 'Introdu o parolă',
    searchMessage: '{0} rezultate disponibile',
    selectionMessage: '{0} elemente selectate',
    emptySelectionMessage: 'Niciun element selectat',
    emptySearchMessage: 'Nu am găsit rezultate',
    emptyFilterMessage: 'Nu am găsit rezultate',
    emptyMessage: 'Nicio opțiune disponibilă',
    dayNames: ['duminică', 'luni', 'marți', 'miercuri', 'joi', 'vineri', 'sâmbătă'],
    dayNamesShort: ['dum', 'lun', 'mar', 'mie', 'joi', 'vin', 'sâm'],
    dayNamesMin: ['D', 'L', 'Ma', 'Mi', 'J', 'V', 'S'],
    monthNames: ['ianuarie', 'februarie', 'martie', 'aprilie', 'mai', 'iunie', 'iulie', 'august', 'septembrie', 'octombrie', 'noiembrie', 'decembrie'],
    monthNamesShort: ['ian', 'feb', 'mar', 'apr', 'mai', 'iun', 'iul', 'aug', 'sep', 'oct', 'nov', 'dec'],
    today: 'Azi',
    firstDayOfWeek: 1,
    dateFormat: 'dd.mm.yy',
  },
}
