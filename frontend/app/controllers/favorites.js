/* globals alert,setInterval,sessionStorage */


import Ember from 'ember';

export default Ember.Controller.extend({
  isProfilePopupVisible: false,
  username: null,
  email: null,
  favouriteDocuments: [],
  searchQuery: '',

  filteredFavouriteDocuments: Ember.computed('favouriteDocuments.[]', 'searchQuery', function() {
    const searchQuery = this.get('searchQuery').toLowerCase();
    const favouriteDocuments = this.get('favouriteDocuments');

    if (!searchQuery) {
      return favouriteDocuments;
    }

    return favouriteDocuments.filter(doc => doc.title.toLowerCase().includes(searchQuery));
  }),

  init() {
    this._super(...arguments);
    this.validateSession();

    setInterval(() => {
      if (this.username) {
        this.loadFavouriteDocuments(this.username);
      }
    }, 5000);
  },

  validateSession() {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/SessionValidationServlet',
      type: 'GET',
      headers: {
        'X-CSRF-Token': this.get('csrfToken')
      },
      xhrFields: {
        withCredentials: true
      },
      success: (response) => {
        if (response.status === "success") {
          this.setProperties({
            username: response.username,
            email: response.email
          });

          if (!sessionStorage.getItem('dashboardReloaded')) {
            sessionStorage.setItem('dashboardReloaded', 'true');
            location.reload();
          }

          this.loadFavouriteDocuments(response.username);
        } else {
          this.transitionToRoute('login');
        }
      },
      error: () => {
        this.transitionToRoute('login');
      }
    });
  },

  loadFavouriteDocuments() {
    const email = this.get('email');
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/GetFavouriteDocumentsServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ email }),
      headers: {
        'X-CSRF-Token': this.get('csrfToken')
      },
      xhrFields: {
        withCredentials: true
      },
      success: (response) => {
        if (response.status === "success") {
          this.set('favouriteDocuments', response.documents);
        } else {
          console.warn("Failed to fetch favourite documents:", response.message);
        }
      },
      error: () => {
        alert("Error fetching favourite documents.");
      }
    });
  },

  actions: {
    toggleProfilePopup() {
      this.toggleProperty('isProfilePopupVisible');
    },
    logout() {
      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/LogoutServlet',
        type: 'POST',
        headers: {
          'X-CSRF-Token': this.get('csrfToken')
        },
        xhrFields: {
          withCredentials: true
        },
        success: () => {
          sessionStorage.removeItem('dashboardReloaded');
          this.transitionToRoute('login');
        },
        error: () => {
          this.transitionToRoute('login');
        }
      });
    },
    createDocument() {
      const uniqueId = this.generateUniqueId();
      const email = this.get('email');
      const url = `/document/${uniqueId}`;

      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/SaveDocumentServlet',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ email, uniqueId }),
        headers: {
          'X-CSRF-Token': this.get('csrfToken')
        },
        xhrFields: {
          withCredentials: true
        },
        success: () => {
          window.open(url, '_blank');
        },
        error: () => {
          alert("Error saving document.");
        }
      });
    },
    openDocument(uniqueId) {
      const url = `/document/${uniqueId}`;
      window.open(url, '_blank');
    },
    updateSearchQuery(query) {
      this.set('searchQuery', query);
    }
  },

  generateUniqueId() {
    let uuid;

    if (typeof window !== 'undefined' && window.crypto && window.crypto.randomUUID) {
      uuid = window.crypto.randomUUID();
    } else {
      uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
      });
    }

    const timestamp = Date.now().toString(36);
    return `${uuid}-${timestamp}`;
  }
});
