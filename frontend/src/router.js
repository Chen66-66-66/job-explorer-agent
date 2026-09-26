import { createRouter, createWebHistory } from 'vue-router'
import CompaniesView from './views/CompaniesView.vue'
import CompanyView from './views/CompanyView.vue'
import PositionView from './views/PositionView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: CompaniesView },
    { path: '/companies/:id', component: CompanyView, props: true },
    { path: '/positions/:id', component: PositionView, props: true },
  ],
})
