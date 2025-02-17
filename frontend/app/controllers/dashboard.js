/* globals alert,sessionStorage,setInterval,event */
import Ember from 'ember';

export default Ember.Controller.extend({
  isProfilePopupVisible: false,
  username: null,
  email: null,
  userDocuments: [],
  searchQuery: '',
  documentToDelete: null,

  filteredDocuments: Ember.computed('userDocuments.[]', 'searchQuery', function() {
    const searchQuery = this.get('searchQuery').toLowerCase();
    const userDocuments = this.get('userDocuments');

    if (!searchQuery) {
      return userDocuments;
    }

    return userDocuments.filter(doc => doc.title.toLowerCase().includes(searchQuery));
  }),

  init() {
    this._super(...arguments);
    this.validateSession();

    setInterval(() => {
      this.loadUserDocuments();
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

          this.loadUserDocuments(response.username);
        } else {
          this.transitionToRoute('login');
        }
      },
      error: () => {
        this.transitionToRoute('login');
      }
    });
  },

  loadUserDocuments() {
    const email = this.get('email');

    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/GetUserDocumentsServlet',
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
        this.set('userDocuments', response.documents);
      },
      error: () => {
        alert("Error fetching user documents.");
      }
    });
  },

  deleteDocument(document) {
    const email = this.get('email');
    const uniqueId = document.uniqueId;

    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/DeleteDocumentServlet',
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
        this.loadUserDocuments();
      },
      error: () => {
        alert("Error deleting document.");
      }
    });
  },

  actions: {
    confirmDelete(document) {
      event.stopPropagation();
      this.set('documentToDelete', document);
    },

    deleteDocumentConfirmed() {
      const document = this.get('documentToDelete');
      if (document) {
        this.deleteDocument(document);
        this.set('documentToDelete', null);
      }
    },

    cancelDelete() {
      this.set('documentToDelete', null);
    },

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
