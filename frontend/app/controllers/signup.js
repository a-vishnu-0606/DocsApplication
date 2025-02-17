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
    signup() {
      let username = document.getElementById('username').value;
      let email = document.getElementById('email').value;
      let password = document.getElementById('password').value;
      let confirmPassword = document.getElementById('confirmPassword').value;

      if (password !== confirmPassword) {
        this.showError("Passwords do not match!");
        return;
      }

      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/SignupServlet',
        type: 'POST',
        contentType: 'application/json',
        headers: {
          'X-CSRF-Token': this.get('csrfToken')
        },
        data: JSON.stringify({
          username: username,
          email: email,
          password: password
        }),
        success: () => {
          this.setProperties({
            errorMessage: null,
            username: '',
            email: '',
            password: '',
            confirmPassword: ''
          });
          this.transitionToRoute('login');
        },

        error: (xhr) => {
          if (xhr.status === 409) {
            this.showError('Email already exists, try logging in.');
          } else {
            this.showError('Signup failed. Please try again.');
          }
          console.error(xhr.responseText);
        }

      });
    }
  },

  showError(message) {
    this.set('errorMessage', message);

    setTimeout(() => {
      this.set('errorMessage', null);
    }, 3000);
  }
});
