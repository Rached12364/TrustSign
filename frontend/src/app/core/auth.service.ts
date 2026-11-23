import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Session } from './models';

const KEY = 'trustsign.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly state = signal<Session | null>(this.restore());
  readonly session = this.state.asReadonly();
  readonly isLoggedIn = computed(() => this.state() !== null);
  readonly role = computed(() => this.state()?.role ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  login(username: string, password: string): Observable<Session> {
    return this.http.post<Session>('/api/auth/login', { username, password }).pipe(
      tap(s => {
        this.state.set(s);
        try { sessionStorage.setItem(KEY, JSON.stringify(s)); } catch { /* stockage indisponible */ }
      })
    );
  }

  logout(): void {
    this.state.set(null);
    try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
    this.router.navigate(['/login']);
  }

  token(): string | null {
    return this.state()?.token ?? null;
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as Session) : null;
    } catch {
      return null;
    }
  }
}
