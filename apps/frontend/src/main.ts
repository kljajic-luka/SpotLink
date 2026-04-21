import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling } from '@angular/router';

import { AppComponent } from './app/app';
import { routes } from './app/app.routes';
import { authCredentialsInterceptor } from '@foundation/networking/interceptors/auth-credentials.interceptor';
import { apiErrorInterceptor } from '@foundation/networking/interceptors/api-error.interceptor';
import { retryInterceptor } from '@foundation/networking/interceptors/retry.interceptor';

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(
      routes,
      withInMemoryScrolling({
        scrollPositionRestoration: 'enabled',
        anchorScrolling: 'enabled',
      }),
    ),
    provideHttpClient(
      withFetch(),
      withInterceptors([authCredentialsInterceptor, retryInterceptor, apiErrorInterceptor]),
    ),
  ],
}).catch((error) => console.error(error));
