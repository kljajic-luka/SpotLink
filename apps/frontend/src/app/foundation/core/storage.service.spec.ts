import { StorageService } from './storage.service';

describe('StorageService', () => {
  let service: StorageService;

  beforeEach(() => {
    service = new StorageService();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('cuva i cita vrednosti iz local storage-a', () => {
    const payload = {
      ime: 'Ana',
      aktivan: true,
    };

    service.set('profil', payload);

    expect(service.get<typeof payload>('profil')).toEqual(payload);
  });

  it('uklanja kljuc iz storage-a', () => {
    service.set('token', 'abc123');
    service.remove('token');

    expect(service.get<string>('token')).toBeNull();
  });

  it('clearNamespace brise samo spotlink kljuceve', () => {
    service.set('sesija', { id: 1 });
    window.localStorage.setItem('drugi.sistem', 'ostaje');

    service.clearNamespace();

    expect(service.get('sesija')).toBeNull();
    expect(window.localStorage.getItem('drugi.sistem')).toBe('ostaje');
  });
});
