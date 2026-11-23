import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditEntry, ChainReport, Envelope, SignerOption, VerifyResponse } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  signers(): Observable<SignerOption[]> {
    return this.http.get<SignerOption[]>('/api/signers');
  }

  envelopes(): Observable<Envelope[]> {
    return this.http.get<Envelope[]>('/api/envelopes');
  }

  envelope(id: number): Observable<Envelope> {
    return this.http.get<Envelope>(`/api/envelopes/${id}`);
  }

  create(title: string, file: File, signers: string[]): Observable<Envelope> {
    const form = new FormData();
    form.append('title', title);
    form.append('file', file);
    signers.forEach(s => form.append('signers', s));
    return this.http.post<Envelope>('/api/envelopes', form);
  }

  sign(id: number): Observable<Envelope> {
    return this.http.post<Envelope>(`/api/envelopes/${id}/sign`, {});
  }

  download(id: number): Observable<Blob> {
    return this.http.get(`/api/envelopes/${id}/download`, { responseType: 'blob' });
  }

  verify(file: File): Observable<VerifyResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<VerifyResponse>('/api/verify', form);
  }

  archive(): Observable<ChainReport> {
    return this.http.get<ChainReport>('/api/archive');
  }

  verifyArchive(): Observable<ChainReport> {
    return this.http.post<ChainReport>('/api/archive/verify', {});
  }

  audit(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>('/api/audit');
  }
}
