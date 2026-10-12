import { Routes } from '@angular/router';

import { Login } from './login/login';
import { Layout } from './layout/layout';
import { Dashboard } from './dashboard/dashboard';

import { Clients } from './clients/clients';
import { IncomingEmails } from './incoming-emails/incoming-emails';

import { Quotationrequest } from './quotationrequest/quotationrequest';
import { Quotation } from './quotation/quotation';

import { MissingInformation } from './missing-information/missing-information';

import { Reports } from './reports/reports';
import { Settings } from './settings/settings';
import { ClientQuotation } from './client-quotation/client-quotation';

export const routes: Routes = [

  // Login
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },

  {
    path: 'login',
    component: Login
  },

  {
  path: 'client-quotation',
  component: ClientQuotation
  },


  // Main Layout
  {
    path: '',
    component: Layout,

    children: [

      // Dashboard
      {
        path: 'dashboard',
        component: Dashboard
      },

      // Incoming Emails
      {
        path: 'incoming-emails',
        component: IncomingEmails
      },

      // Quotation Requests
      {
        path: 'quotation-requests',
        component: Quotationrequest
      },

      // Clients
      {
        path: 'clients',
        component: Clients
      },

      // Quotations
      {
        path: 'quotations',
        component: Quotation
      },

      // Missing Information
      {
        path: 'missing-information',
        component: MissingInformation
      },

      // Reports
      {
        path: 'reports',
        component: Reports
      },

      // Settings
      {
        path: 'settings',
        component: Settings
      }

    ]
  }

];