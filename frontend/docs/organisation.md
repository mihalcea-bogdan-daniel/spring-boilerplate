# Organisation, supervision and "sign in as"

## The model

- **Units** form a tree with one root, the organisation itself: Company → Department → Team.
- **Ranks** are a per-tenant catalogue. The number decides who outranks whom; names (per language) and
  synonyms are labels only. Seeded:

  | Level | en | ro | Supervises |
  | ---: | --- | --- | :---: |
  | 900 | Director | Director | yes |
  | 500 | Head of department | Șef de departament | yes |
  | 100 | Chief | Șef | yes |
  | 80 | Deputy chief | Adjunct | yes |
  | 50 | Coordinator | Coordonator | yes |
  | 10 | Member | Membru | no |

- **Memberships:** a person holds one rank in a unit, and may belong to several units.

**A supervises B** when A holds a supervising rank in unit U, B is in U or in any unit below it, and A's
level is higher than B's. Equal levels are peers, and disabled people neither supervise nor count. A
"global" role is simply a supervising rank in the root unit: the Director at the root supervises everyone
below 900.

```
Acme Inc. (root)        alice  Director 900          supervises everyone
├── Engineering         bob    Head of department    Platform + Mobile people
│   ├── Platform team   carol  Chief 100             dave, erin
│   │                   dave   Deputy chief 80       erin
│   │                   erin   Member 10             nobody
│   └── Mobile team     frank  Chief 100 · gina Member 10
└── Sales               hana   Head 500 · ivan Coordinator 50
```

The demo data (`APP_SEED=true`) builds exactly this in `acme`, with password `Demo-Passw0rd!` for
`<name>@acme.test`.

## What supervising someone allows

| Power | Who | Where |
| --- | --- | --- |
| See the organigram | `org:read` (every role) | Organisation |
| See your people, their status and activity | anyone who supervises someone | My team |
| Read what your people (or units you lead) own | supervisors, read only | any owned module, through the [access model](../../backend/docs/access.md) |
| Add or remove people and give ranks **below your own** in units you lead | leaders | Organisation → unit panel |
| Sign in as one of your people | supervisors with `org:impersonate` | My team → Sign in as |
| Create, move, delete units; edit ranks; any membership | `org:manage` (TENANT_ADMIN) | Organisation, Administration → Ranks |

## "Sign in as"

- It opens a 30-minute session as the other person **in a new tab**. The session lives in that tab only
  (sessionStorage), so the supervisor stays themself everywhere else. "End session" returns the tab to
  them.
- **Refused when** the person also supervises you, or holds any permission you don't (no escalation).
- **Off limits inside the session:** password, MFA, passkeys, sessions, API keys and another "sign in as".
- **Visibility:** every start is in the audit log, and the person sees it in Profile → Account access.

## In code

- **Backend:** `OrgService.supervises(a, b)` / `subordinateIds(a)`, `@org.supervises(#userId)` and
  `@org.leads(#unitId)` in `@PreAuthorize`. The hierarchy is not in the JWT: it is computed per request
  (recursive CTEs, memoised for the request).
- **Frontend:** `useRanks()` gives rank names in the current language; `<RankTag :rank="rankId" />` shows
  one. The chart is PrimeVue's `OrganizationChart` (`pages/organization.vue`).
