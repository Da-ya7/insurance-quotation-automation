// import { Routes } from '@angular/router';

// import { Login } from './login/login';
// import { Layout } from './layout/layout';
// import { Dashboard } from './dashboard/dashboard';

// import { Clients } from './clients/clients';
// import { IncomingEmails } from './incoming-emails/incoming-emails';

// import { Quotationrequest } from './quotationrequest/quotationrequest';
// import { Quotation } from './quotation/quotation';

// import { MissingInformation } from './missing-information/missing-information';

// import { Reports } from './reports/reports';
// import { Settings } from './settings/settings';
// import { ClientQuotation } from './client-quotation/client-quotation';
// import { ClientForm } from './client-form/client-form';
// import { MasterData } from './master-data/master-data';
// import { authGuard } from './core/auth.guard';

// import { ClientMaster } from './client-master/client-master';

// export const routes: Routes = [

//   // Login
//   {
//     path: '',
//     redirectTo: 'login',
//     pathMatch: 'full'
//   },

//   {
//     path: 'login',
//     component: Login
//   },

//   // Public pages (no login, no layout)
//   {
//     path: 'client-quotation',
//     component: ClientQuotation
//   },

//   {
//     path: 'form',
//     component: ClientForm
//   },

//   {
//     path: 'form/:type',
//     component: ClientForm
//   },


//   // Main Layout
//   {
//     path: '',
//     component: Layout,
//     canActivate: [authGuard],

//     children: [

//       // Dashboard
//       {
//         path: 'dashboard',
//         component: Dashboard
//       },

//       // Incoming Emails
//       {
//         path: 'incoming-emails',
//         component: IncomingEmails
//       },

//       // Quotation Requests
//       {
//         path: 'quotation-requests',
//         component: Quotationrequest
//       },

//       // Clients
//       {
//         path: 'clients',
//         component: Clients
//       },

//       // Quotations
//       {
//         path: 'quotations',
//         component: Quotation
//       },

//       // Missing Information
//       {
//         path: 'missing-information',
//         component: MissingInformation
//       },

//       // Master data
//       {
//         path: 'master-data',
//         component: MasterData
//       },

//       // Reports
//       {
//         path: 'reports',
//         component: Reports
//       },

//       // Settings
//       {
//         path: 'settings',
//         component: Settings
//       }

//     ]
//   },

//   // Unknown route
//   {
//     path: '**',
//     redirectTo: 'dashboard'
//   },

//   {
//   path: 'master-data/client-master',
//   component: ClientMaster
// }

// ];




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
import { ClientForm } from './client-form/client-form';
import { MasterData } from './master-data/master-data';
import { ClientMaster } from './master-data/client-master/client-master';

import { authGuard } from './core/auth.guard';

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

  // Public pages (no login, no layout)
  {
    path: 'client-quotation',
    component: ClientQuotation
  },

  {
    path: 'form',
    component: ClientForm
  },

  {
    path: 'form/:type',
    component: ClientForm
  },

  // Main Layout
  {
    path: '',
    component: Layout,
    canActivate: [authGuard],

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

      // Master Data
      {
        path: 'master-data',
        component: MasterData
      },

      // Client Master
      {
        path: 'master-data/client-master',
        component: ClientMaster
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
  },

  // Unknown route
  {
    path: '**',
    redirectTo: 'dashboard'
  }

];

