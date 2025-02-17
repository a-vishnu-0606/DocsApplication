import Ember from 'ember';

export default Ember.Controller.extend({
  errorMessage: null,

  init() {
    this._super(...arguments);
    this.checkSession();
  },

  checkSession() {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/SessionValidationServlet',
      type: 'GET',
      xhrFields: {
        withCredentials: true
      },
      success: (response) => {
        if (response.status === "success") {
          this.transitionToRoute('dashboard');
        }
      },
      error: () => {

      }
    });
  },

  actions: {
    login() {
      let email = document.getElementById('email').value;
      let password = document.getElementById('password').value;

      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/LoginServlet',
        type: 'POST',
        contentType: 'application/json',
        headers: {
          'X-CSRF-Token': this.get('csrfToken')
        },
        data: JSON.stringify({
          email: email,
          password: password
        }),
        xhrFields: {
          withCredentials: true
        },
        success: (response) => {
          if (response.status === "success") {
            this.setProperties({
              errorMessage: null,
              email: '',
              password: '',
              csrfToken: response.csrfToken
            });
            this.transitionToRoute('dashboard');
          } else {
            this.showError(response.message || "Invalid credentials, please try again.");
          }
        },

        error: (xhr) => {
          let errorMessage = xhr.responseJSON ? xhr.responseJSON.message : "Login failed. Check your credentials.";
          this.showError(errorMessage);
        }
      });
    },

    gotosignup() {
      this.transitionToRoute('signup');
    }
  },

  showError(message) {
    this.set('errorMessage', message);

    setTimeout(() => {
      this.set('errorMessage', null);
    }, 3000);
  }
});
