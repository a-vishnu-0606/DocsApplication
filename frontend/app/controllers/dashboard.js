/* globals alert,sessionStorage,setInterval,FileReader,mammoth,event */
import Ember from 'ember';

export default Ember.Controller.extend({
  isProfilePopupVisible: false,
  username: null,
  email: null,
  userDocuments: [],
  searchQuery: '',
  documentToDelete: null,
  notifications: [],
  isNotificationsVisible: false,


  filteredDocuments: Ember.computed('userDocuments.[]', 'searchQuery', function() {
    const searchQuery = this.get('searchQuery').toLowerCase();
    const userDocuments = this.get('userDocuments');

    if (!searchQuery) {
      return userDocuments;
    }

    return userDocuments.filter(doc => doc.title.toLowerCase().includes(searchQuery));
  }),

  unreadNotificationsCount: Ember.computed('notifications.[]', function() {
    return this.get('notifications').filterBy('is_read', false).length;
  }),

  init() {
    this._super(...arguments);
    this.validateSession();

    setInterval(() => {
      this.loadUserDocuments();
      this.loadNotifications();
    }, 5000);
  },

  logoutAndRedirect() {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/LogoutServlet',
      type: 'POST',
      headers: {
        'X-CSRF-Token': this.get('csrfToken'),
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
          this.loadNotifications();
        } else {
          this.logoutAndRedirect();
        }
      },
      error: () => {
        this.logoutAndRedirect();
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
      error: (xhr) => {
        if (xhr.status === 401) {
          this.logoutAndRedirect();
        } else {
          alert("Error fetching user documents.");
        }
      }
    });
  },

  loadNotifications() {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/GetNotificationsServlet',
      type: 'GET',
      headers: {
        'X-CSRF-Token': this.get('csrfToken')
      },
      xhrFields: {
        withCredentials: true
      },
      success: (response) => {
        if (response.status === "success") {
          this.set('notifications', response.notifications);
        } else {
          console.warn("Failed to fetch notifications:", response.message);
        }
      },
      error: (xhr, status, error) => {
        if (xhr.status === 401) {
          this.logoutAndRedirect();
        }
        console.error("AJAX Error:", status, error);
      }
    });
  },
  markNotificationsAsRead() {
    const email = this.get('email');
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/MarkNotificationsAsReadServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ email }),
      headers: {
        'X-CSRF-Token': this.get('csrfToken')
      },
      xhrFields: {
        withCredentials: true
      },
      success: () => {
        this.loadNotifications();
      },
      error: (xhr) => {
        if (xhr.status === 401) {
          this.logoutAndRedirect();
        }
        console.error("Error marking notifications as read.");
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
      error: (xhr) => {
        if (xhr.status === 401) {
          this.logoutAndRedirect();
        }else {
          alert("Error deleting document.");
        }
      }
    });
  },
  saveUploadedDocument(title, content) {
    const email = this.get('email');
    const uniqueId = this.generateUniqueId();

    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/UploadDocumentServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ email, uniqueId, title, content }),
      headers: {
        'X-CSRF-Token': this.get('csrfToken')
      },
      xhrFields: {
        withCredentials: true
      },
      success: (response) => {
        if (response.status === "success") {
          this.loadUserDocuments();
          const url = `/document/${uniqueId}`;
          window.open(url, '_blank');
        } else {
          alert("Error saving uploaded document.");
        }
      },
      error: (xhr) => {
        if (xhr.status === 401) {
          this.logoutAndRedirect();
        }else {
          alert("Error saving uploaded document.");
        }
      }
    });
  },

  actions: {

    markNotificationsAsRead() {
      this.markNotificationsAsRead();
    },
    handleFileUpload(event) {
      const file = event.target.files[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (e) => {
          const arrayBuffer = e.target.result;
          mammoth.extractRawText({ arrayBuffer: arrayBuffer })
            .then((result) => {
              const content = result.value;
              const title = file.name.replace(/\.[^/.]+$/, "");
              this.saveUploadedDocument(title, content);
            })
            .catch((error) => {
              console.error("Error extracting content:", error);
            });
        };
        reader.readAsArrayBuffer(file);
      }
    },

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
    toggleNotifications() {
      this.toggleProperty('isNotificationsVisible');
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
          this.logoutAndRedirect();
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
        error: (xhr) => {
          if (xhr.status === 401) {
            this.logoutAndRedirect();
          }else {
            alert("Error saving document.");
          }
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
