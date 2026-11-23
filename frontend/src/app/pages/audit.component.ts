import { Component, OnInit, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../core/api.service';
import { AuditEntry } from '../core/models';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [DatePipe],
  template: `
  <div class="stack">
    <div class="head">
      <div>
        <p class="eyebrow">Traçabilité</p>
        <h1>Journal d'audit</h1>
        <p class="muted">Connexions, créations de dossiers, signatures, archivages, vérifications et alertes : qui, quand, quoi.</p>
      </div>
      <button class="btn" (click)="load()">Actualiser</button>
    </div>
    <div class="kpis">
      <div class="kpi card"><b>{{ n('SIGNATURE') }}</b><span>signatures</span></div>
      <div class="kpi card"><b>{{ n('ARCHIVAGE') }}</b><span>documents archivés</span></div>
      <div class="kpi card"><b>{{ n('VERIFICATION') }}</b><span>vérifications</span></div>
      <div class="kpi card"><b>{{ alerts() }}</b><span>alertes</span></div>
    </div>
    <div class="table-wrap card">
      <table>
        <thead><tr><th>Date</th><th>Utilisateur</th><th>Action</th><th>Détail</th></tr></thead>
        <tbody>
          @for (e of entries(); track e.id) {
            <tr>
              <td class="nowrap">{{ e.time | date:'dd/MM HH:mm:ss' }}</td>
              <td><b>{{ e.username }}</b></td>
              <td><span class="act" [attr.data-a]="e.action">{{ e.action }}</span></td>
              <td class="detail">{{ e.detail }}</td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="muted">Aucun événement.</td></tr>
          }
        </tbody>
      </table>
    </div>
  </div>
  `
})
export class AuditComponent implements OnInit {
  entries = signal<AuditEntry[]>([]);
  alerts = computed(() => this.entries().filter(e => e.action.startsWith('ALERTE')).length);

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void { this.api.audit().subscribe(e => this.entries.set(e)); }

  n(action: string): number { return this.entries().filter(e => e.action === action).length; }
}
