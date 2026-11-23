import { Component, OnInit, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Envelope, SignerOption } from '../core/models';
import { STATUS_LABEL } from '../core/labels';

type Filter = 'todo' | 'progress' | 'done' | 'all';

@Component({
  selector: 'app-envelopes',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
  <div class="stack">
    <div class="head">
      <div>
        <p class="eyebrow">Circuit de signature</p>
        <h1>Dossiers</h1>
        <p class="muted">Chaque dossier contient un PDF et la liste ordonnée de ses signataires. Le document est archivé automatiquement après la dernière signature.</p>
      </div>
      @if (canCreate()) {
        <button class="btn primary" (click)="showForm.set(!showForm())">{{ showForm() ? 'Fermer' : 'Nouveau dossier' }}</button>
      }
    </div>

    @if (showForm()) {
      <form class="card create" (ngSubmit)="create()">
        <div class="grid2">
          <label>Titre du dossier
            <input name="t" [(ngModel)]="title" placeholder="Ex. : Contrat de maintenance 2027" required>
          </label>
          <label class="file">Document PDF
            <input type="file" accept="application/pdf" (change)="pick($event)">
            <span>{{ file?.name ?? 'Choisir un PDF…' }}</span>
          </label>
        </div>
        <div>
          <p class="label">Signataires, dans l'ordre de signature</p>
          <div class="chips">
            @for (s of options(); track s.username) {
              <button type="button" class="chip" [class.on]="order().includes(s.username)" (click)="toggle(s.username)">
                @if (order().includes(s.username)) { <b class="num">{{ order().indexOf(s.username) + 1 }}</b> }
                {{ s.displayName }}
              </button>
            }
          </div>
          <p class="hint">Cliquez dans l'ordre voulu. Cliquez de nouveau pour retirer.</p>
        </div>
        @if (formError()) { <p class="form-error">{{ formError() }}</p> }
        <div><button class="btn primary" [disabled]="busy()">{{ busy() ? 'Création…' : 'Créer le dossier' }}</button></div>
      </form>
    }

    <div class="tabs">
      <button [class.on]="filter() === 'todo'" (click)="filter.set('todo')">À signer <span>{{ count('todo') }}</span></button>
      <button [class.on]="filter() === 'progress'" (click)="filter.set('progress')">En cours <span>{{ count('progress') }}</span></button>
      <button [class.on]="filter() === 'done'" (click)="filter.set('done')">Signés et archivés <span>{{ count('done') }}</span></button>
      <button [class.on]="filter() === 'all'" (click)="filter.set('all')">Tous <span>{{ count('all') }}</span></button>
    </div>

    <div class="list">
      @for (e of shown(); track e.id) {
        <a class="card row" [routerLink]="['/dossiers', e.id]">
          <div class="row-main">
            <b>{{ e.title }}</b>
            <small>{{ e.fileName }} · créé le {{ e.createdAt | date:'dd/MM/yyyy' }} par {{ e.createdBy }}</small>
          </div>
          <div class="steps-mini">
            @for (s of e.steps; track s.position) {
              <span class="dot" [class.done]="s.signedAt" [class.now]="s.current" [title]="s.displayName">{{ initials(s.displayName) }}</span>
            }
          </div>
          <div class="row-end">
            @if (e.myTurn) { <span class="pill turn">À vous de signer</span> }
            <span class="pill" [attr.data-s]="e.status">{{ status(e) }}</span>
          </div>
        </a>
      } @empty {
        <p class="muted empty-list">Aucun dossier dans cette catégorie.</p>
      }
    </div>
  </div>
  `
})
export class EnvelopesComponent implements OnInit {
  all = signal<Envelope[]>([]);
  options = signal<SignerOption[]>([]);
  order = signal<string[]>([]);
  filter = signal<Filter>('all');
  showForm = signal(false);
  busy = signal(false);
  formError = signal('');
  title = '';
  file: File | null = null;

  canCreate = computed(() => this.auth.role() === 'USER');
  shown = computed(() => this.all().filter(e => this.match(e, this.filter())));

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.api.envelopes().subscribe(list => {
      this.all.set(list);
      this.filter.set(list.some(e => e.myTurn) ? 'todo' : 'all');
    });
    if (this.canCreate()) this.api.signers().subscribe(s => this.options.set(s));
  }

  count(f: Filter): number {
    return this.all().filter(e => this.match(e, f)).length;
  }

  status(e: Envelope): string {
    return e.status === 'EN_COURS' ? `${STATUS_LABEL[e.status]} · ${e.signedCount}/${e.steps.length}` : STATUS_LABEL[e.status];
  }

  initials(name: string): string {
    return name.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }

  toggle(u: string): void {
    this.order.update(o => (o.includes(u) ? o.filter(x => x !== u) : [...o, u]));
  }

  pick(e: Event): void {
    this.file = (e.target as HTMLInputElement).files?.[0] ?? null;
  }

  create(): void {
    if (!this.title.trim() || !this.file || this.order().length === 0) {
      this.formError.set('Il faut un titre, un PDF et au moins un signataire.');
      return;
    }
    this.busy.set(true);
    this.formError.set('');
    this.api.create(this.title, this.file, this.order()).subscribe({
      next: env => this.router.navigate(['/dossiers', env.id]),
      error: err => {
        this.formError.set(err?.error?.message ?? 'Création impossible.');
        this.busy.set(false);
      }
    });
  }

  private match(e: Envelope, f: Filter): boolean {
    switch (f) {
      case 'todo': return e.myTurn;
      case 'progress': return e.status === 'EN_COURS';
      case 'done': return e.status === 'SIGNE_ARCHIVE';
      default: return true;
    }
  }
}
