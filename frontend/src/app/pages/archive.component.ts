import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ChainReport } from '../core/models';
import { shortHash } from '../core/labels';

@Component({
  selector: 'app-archive',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
  <div class="stack">
    <div class="head">
      <div>
        <p class="eyebrow">Archivage à valeur probante</p>
        <h1>Registre d'archive</h1>
        <p class="muted">Chaque document signé est inscrit dans un registre où chaque entrée contient l'empreinte de la précédente. Modifier, supprimer ou insérer une entrée, ou retoucher un fichier archivé, casse la chaîne à cet endroit.</p>
      </div>
      <button class="btn" (click)="verify()" [disabled]="busy()">{{ busy() ? 'Contrôle…' : 'Contrôler toute l\\'archive' }}</button>
    </div>

    @if (report(); as r) {
      <div class="verdict" [attr.data-t]="r.intact ? 'ok' : 'bad'">
        <div class="v-icon">{{ r.intact ? '✓' : '✕' }}</div>
        <div><b>{{ r.intact ? 'Archive intacte' : 'Archive altérée' }}</b><p>{{ r.message }}</p></div>
      </div>

      <div class="chain">
        @for (c of r.checks; track c.position) {
          <div class="link-arrow" [class.broken]="!c.linkOk">{{ c.linkOk ? '↓' : '✕' }} <code>{{ short(c.previousHash) }}</code></div>
          <article class="card block" [class.bad]="!c.ok">
            <header>
              <span class="sig-n">{{ c.position }}</span>
              <div>
                <a [routerLink]="['/dossiers', c.envelopeId]"><b>{{ c.title }}</b></a>
                <small>archivé le {{ c.archivedAt | date:'dd/MM/yyyy à HH:mm:ss' }}</small>
              </div>
            </header>
            <dl class="proofs small">
              <dt>Empreinte du document</dt><dd><code [title]="c.documentSha256">{{ short(c.documentSha256) }}</code> <i [class.ok]="c.fileOk">{{ c.fileOk ? 'fichier intact' : 'fichier modifié' }}</i></dd>
              <dt>Empreinte de l'entrée</dt><dd><code [title]="c.entryHash">{{ short(c.entryHash) }}</code> <i [class.ok]="c.hashOk">{{ c.hashOk ? 'cohérente' : 'ne correspond plus' }}</i></dd>
            </dl>
          </article>
        } @empty {
          <p class="muted">Aucun document archivé pour le moment.</p>
        }
      </div>
    }
  </div>
  `
})
export class ArchiveComponent implements OnInit {
  report = signal<ChainReport | null>(null);
  busy = signal(false);
  short = shortHash;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.archive().subscribe(r => this.report.set(r));
  }

  verify(): void {
    this.busy.set(true);
    this.api.verifyArchive().subscribe({
      next: r => { this.report.set(r); this.busy.set(false); },
      error: () => this.busy.set(false)
    });
  }
}
