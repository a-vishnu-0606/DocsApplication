import Ember from 'ember';

export default Ember.Controller.extend({
  isIndexRoute: true,

  updateRouteState: Ember.observer('currentPath', function () {
    this.set('isIndexRoute', this.get('currentPath') === 'index');
  }),

  actions: {
    navigateTo(routeName) {
      this.transitionToRoute(routeName);
    }
  }
});
