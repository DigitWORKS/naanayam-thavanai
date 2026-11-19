import { HttpClient, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { API } from '../../core/api';
import { apiErrorMessage } from '../../core/api-error';
import { Money } from '../../core/money';
import { safeValue } from '../../core/resource';
import { Dialog } from '../../shared/dialog';
import { formatPhone } from '../../shared/format';

/** GET /users?role=AGENT */
interface AgentUser {
  id: number;
  name: string;
  phone: string;
  isActive: boolean;
  areas: { id: number; name: string; isActive: boolean }[];
  lastLoginAt: string | null;
}

/** GET /areas */
interface Area {
  id: number;
  name: string;
  isActive: boolean;
}

/** GET /areas/overview — adds counts and agents to the plain list. */
interface AreaOverview {
  id: number;
  name: string;
  isActive: boolean;
  clientCount: number;
  agents: { id: number; name: string }[];
}

type Action =
  | { kind: 'areas'; agent: AgentUser }
  | { kind: 'pin'; agent: AgentUser }
  | { kind: 'deactivate'; agent: AgentUser }
  | { kind: 'rename'; area: Area }
  | null;

/** Same rules as the server (UserDtos.PHONE / PIN). */
const PHONE = /^[6-9]\d{9}$/;
const PIN = /^\d{4,6}$/;
const digits = (s: string) => s.replace(/\D/g, '');
const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Agents & areas (design board "Web agents & areas"). */
@Component({
  selector: 'nt-agents',
  imports: [Dialog],
  templateUrl: './agents.html',
  styleUrl: './agents.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgentsPage {
  private readonly http = inject(HttpClient);

  private readonly agentsRes = httpResource<AgentUser[]>(() => ({ url: `${API}/users`, params: { role: 'AGENT' } }));
  /** The areas themselves: always available. */
  private readonly areasRes = httpResource<Area[]>(() => `${API}/areas`);
  /** Client counts and agents per area; if it fails the areas still show, without them. */
  private readonly overviewRes = httpResource<AreaOverview[]>(() => `${API}/areas/overview`);
  private readonly monthRes = httpResource<{ key: string; collected: string }[]>(() => {
    const now = new Date();
    return {
      url: `${API}/reports/collections`,
      params: { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)), to: iso(now), groupBy: 'AGENT' },
    };
  });

  readonly agentsError = computed(() => (this.agentsRes.error() ? apiErrorMessage(this.agentsRes.error()) : ''));
  readonly areasError = computed(() => (this.areasRes.error() ? apiErrorMessage(this.areasRes.error()) : ''));
  readonly agentsLoading = computed(() => this.agentsRes.isLoading() && !this.agentsRes.hasValue());

  readonly agents = computed(() => {
    // The collections report is keyed by collector name.
    const month = new Map((safeValue(this.monthRes) ?? []).map((r) => [r.key, Money.fromJson(r.collected)]));
    return [...(safeValue(this.agentsRes) ?? [])]
      .sort((a, b) => Number(b.isActive) - Number(a.isActive) || a.name.localeCompare(b.name))
      .map((a) => ({
        source: a,
        phone: formatPhone(a.phone),
        areas: a.areas.map((x) => x.name),
        month: (month.get(a.name) ?? Money.zero).format(),
        lastLogin: a.lastLoginAt
          ? `last login ${new Date(a.lastLoginAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}`
          : 'never logged in',
      }));
  });

  readonly agentCountText = computed(() => {
    const list = safeValue(this.agentsRes);
    if (!list) return '';
    const active = list.filter((a) => a.isActive).length;
    const inactive = list.length - active;
    return `${active} active${inactive ? ` · ${inactive} inactive` : ''}`;
  });

  readonly areasLoading = computed(() => this.areasRes.isLoading() && !this.areasRes.hasValue());
  readonly overviewFailed = computed(() => !!this.overviewRes.error() && this.areasRes.hasValue());

  readonly areas = computed(() => {
    const extra = new Map((safeValue(this.overviewRes) ?? []).map((o) => [o.id, o]));
    return [...(safeValue(this.areasRes) ?? [])]
      .sort((a, b) => Number(b.isActive) - Number(a.isActive) || a.name.localeCompare(b.name))
      .map((a) => {
        const o = extra.get(a.id);
        return {
          source: a,
          line: o
            ? `${o.clientCount} ${o.clientCount === 1 ? 'client' : 'clients'} · ${
              o.agents.length ? `Agent: ${o.agents.map((x) => x.name).join(', ')}` : 'No agent yet'}`
            : '',
        };
      });
  });
  readonly activeAreas = computed(() => (safeValue(this.areasRes) ?? []).filter((a) => a.isActive));

  private reloadAll(): void {
    this.agentsRes.reload();
    this.areasRes.reload();
    this.overviewRes.reload();
  }

  retry(): void {
    this.reloadAll();
    this.monthRes.reload();
  }

  // ------------------------------------------------------------ add area

  readonly newArea = signal('');
  readonly areaBusy = signal(false);
  readonly areaError = signal('');

  async addArea(): Promise<void> {
    const name = this.newArea().trim();
    if (!name) return;
    this.areaBusy.set(true);
    this.areaError.set('');
    try {
      await firstValueFrom(this.http.post(`${API}/areas`, { name }));
      this.newArea.set('');
      this.areasRes.reload();
      this.overviewRes.reload();
    } catch (e) {
      this.areaError.set(apiErrorMessage(e, 'Could not add the area.'));
    } finally {
      this.areaBusy.set(false);
    }
  }

  // ------------------------------------------------------------ add agent

  readonly name = signal('');
  readonly phone = signal('');
  readonly pin = signal('');
  readonly pickedAreas = signal<number[]>([]);
  readonly touched = signal(false);
  readonly addBusy = signal(false);
  readonly addError = signal('');
  readonly added = signal('');

  readonly addErrors = computed(() => {
    const e: Record<string, string> = {};
    if (!this.name().trim()) e['name'] = 'Enter the name.';
    if (!PHONE.test(digits(this.phone()))) e['phone'] = 'Enter a 10-digit mobile number.';
    if (!PIN.test(this.pin())) e['pin'] = 'PIN must be 4 to 6 digits.';
    if (!this.pickedAreas().length) e['areas'] = 'Pick at least one area: agents see only their areas.';
    return e;
  });
  readonly shown = computed(() => (this.touched() ? this.addErrors() : {}));

  toggleArea(id: number, on: boolean): void {
    this.pickedAreas.update((ids) => (on ? [...ids, id] : ids.filter((x) => x !== id)));
  }

  async addAgent(): Promise<void> {
    this.touched.set(true);
    this.added.set('');
    if (Object.keys(this.addErrors()).length) return;
    this.addBusy.set(true);
    this.addError.set('');
    try {
      const name = this.name().trim();
      await firstValueFrom(this.http.post(`${API}/users`, {
        name, phone: digits(this.phone()), pin: this.pin(), areaIds: this.pickedAreas(),
      }));
      this.name.set('');
      this.phone.set('');
      this.pin.set('');
      this.pickedAreas.set([]);
      this.touched.set(false);
      this.added.set(`${name} added. Share the PIN in person; they log in to the mobile app with their number.`);
      this.reloadAll();
    } catch (e) {
      this.addError.set(apiErrorMessage(e, 'Could not add the agent.'));
    } finally {
      this.addBusy.set(false);
    }
  }

  // ------------------------------------------------------------ row actions (dialogs)

  readonly action = signal<Action>(null);
  readonly dialog = viewChild(Dialog);
  readonly dlgBusy = signal(false);
  readonly dlgError = signal('');
  readonly dlgAreas = signal<number[]>([]);
  readonly dlgPin = signal('');
  readonly dlgPin2 = signal('');
  readonly dlgName = signal('');

  open(a: Action): void {
    this.dlgError.set('');
    this.dlgPin.set('');
    this.dlgPin2.set('');
    if (a?.kind === 'areas') this.dlgAreas.set(a.agent.areas.map((x) => x.id));
    if (a?.kind === 'rename') this.dlgName.set(a.area.name);
    this.action.set(a);
  }

  toggleDlgArea(id: number, on: boolean): void {
    this.dlgAreas.update((ids) => (on ? [...ids, id] : ids.filter((x) => x !== id)));
  }

  /** Areas for the dialog: active ones plus any inactive one the agent still has. */
  readonly dlgAreaOptions = computed(() => {
    const a = this.action();
    const own = a?.kind === 'areas' ? a.agent.areas.map((x) => x.id) : [];
    return (safeValue(this.areasRes) ?? []).filter((x) => x.isActive || own.includes(x.id));
  });

  readonly pinError = computed(() => {
    if (!PIN.test(this.dlgPin())) return 'PIN must be 4 to 6 digits.';
    if (this.dlgPin() !== this.dlgPin2()) return 'The two PINs do not match.';
    return '';
  });

  async runAction(): Promise<void> {
    const a = this.action();
    if (!a) return;
    this.dlgBusy.set(true);
    this.dlgError.set('');
    try {
      switch (a.kind) {
        case 'areas':
          await firstValueFrom(this.http.put(`${API}/users/${a.agent.id}/areas`, { areaIds: this.dlgAreas() }));
          break;
        case 'pin':
          if (this.pinError()) throw new Error(this.pinError());
          await firstValueFrom(this.http.patch(`${API}/users/${a.agent.id}`, { pin: this.dlgPin() }));
          break;
        case 'deactivate':
          await firstValueFrom(this.http.patch(`${API}/users/${a.agent.id}`, { isActive: false }));
          break;
        case 'rename': {
          const name = this.dlgName().trim();
          if (!name) throw new Error('Enter a name.');
          await firstValueFrom(this.http.patch(`${API}/areas/${a.area.id}`, { name }));
          break;
        }
      }
      this.reloadAll();
      this.dialog()?.close();
    } catch (e) {
      this.dlgError.set(e instanceof Error && !('status' in e) ? e.message : apiErrorMessage(e));
    } finally {
      this.dlgBusy.set(false);
    }
  }

  readonly reactivating = signal<number | null>(null);
  readonly rowError = signal('');

  async reactivate(a: AgentUser): Promise<void> {
    this.reactivating.set(a.id);
    this.rowError.set('');
    try {
      await firstValueFrom(this.http.patch(`${API}/users/${a.id}`, { isActive: true }));
      this.reloadAll();
    } catch (e) {
      this.rowError.set(apiErrorMessage(e, `Could not reactivate ${a.name}.`));
    } finally {
      this.reactivating.set(null);
    }
  }

  dialogHeading(a: NonNullable<Action>): string {
    switch (a.kind) {
      case 'areas': return `${a.agent.name}'s areas`;
      case 'pin': return `New PIN for ${a.agent.name}`;
      case 'deactivate': return `Deactivate ${a.agent.name}?`;
      case 'rename': return `Rename ${a.area.name}`;
    }
  }
}
