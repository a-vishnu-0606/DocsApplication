import Ember from 'ember';

export default Ember.Route.extend({
  actions:{
    gotosignup() {
      this.transitionToRoute('signup');
    }
  }
});
