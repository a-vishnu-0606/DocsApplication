import Ember from 'ember';
import config from './config/environment';

const Router = Ember.Router.extend({
  location: config.locationType
});

Router.map(function() {
  this.route('login');
  this.route('dashboard');
  this.route('document', { path: '/document/:document_id' });
  this.route('signup');
  this.route('sharedwithme');
  this.route('document-not-found');
  this.route('sidebar');
  this.route('favorites');
});

export default Router;
