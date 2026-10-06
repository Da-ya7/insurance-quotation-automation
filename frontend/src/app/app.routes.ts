import { Routes } from '@angular/router';

import { Login } from './login/login';
import { Dashboard } from './dashboard/dashboard';

import { Clients } from './clients/clients';
import { IncomingEmails } from './incoming-emails/incoming-emails';

import { Quotationrequest } from './quotationrequest/quotationrequest';
import { Quotation } from './quotation/quotation';

import { MissingInformation } from './missing-information/missing-information';

import { Reports } from './reports/reports';
import { Settings } from './settings/settings';




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


  // Dashboard
  {
    path: 'dashboard',
    component: Dashboard
  },


  // Clients
  {
    path: 'clients',
    component: Clients
  },


  // Incoming Emails
  {
    path: 'incoming-emails',
    component: IncomingEmails
  },


  {
  path: 'quotation-requests',
  component: Quotationrequest
},

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

];