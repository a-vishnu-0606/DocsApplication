/* globals WebSocket, alert, URL, Blob */

import Ember from 'ember';

export default Ember.Controller.extend({
  documentId: null,
  updatedTitle: null,
  documentTitle: null,
  allUsers: [],
  filteredUsers: [],
  loggedInUserEmail: null,
  documentContent: null,
  socket: null,
  isViewer: false,
  isOwnerOrEditor: false,
  lastUpdatedMessage: null,
  isFavorited: false,
  lastSavedContent: null,

  init() {
    this._super(...arguments);

    this.validateSession().then(() => {
      Ember.run.next(this, function () {
        const documentId = this.get('model.document_id');
        this.set('documentId', documentId);
        this.checkDocumentExists(documentId).then((exists) => {
          if (exists) {
            this.loadDocumentDetails(documentId);
            this.fetchAllUsers();
            this.initWebSocket(documentId);
            this.checkIfFavorited(documentId);
          } else {
            console.log("Document does not exist.");
          }
        });
      });
    }).catch((error) => {
      console.error("Error during session validation:", error);
    });
  },

  checkIfFavorited(documentId) {
    const loggedInUserEmail = this.get('loggedInUserEmail');
    if (!loggedInUserEmail) {
      console.error("Logged in user email is not set.");
      return;
    }

    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/CheckFavouriteServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ uniqueId: documentId, email: loggedInUserEmail }),
      headers: { 'X-CSRF-Token': this.get('csrfToken') },
      xhrFields: { withCredentials: true },
      success: (response) => {
        if (response.status === "success") {
          this.set('isFavorited', response.isFavorited);
        } else {
          console.warn("Failed to check favorite status:", response.message);
        }
      },
      error: (xhr, status, error) => {
        console.error("AJAX Error:", status, error);
      }
    });
  },

  toggleFavorite() {
    const documentId = this.get('documentId');
    const loggedInUserEmail = this.get('loggedInUserEmail');
    const isFavorited = this.get('isFavorited');

    if (!documentId || !loggedInUserEmail) {
      console.error("Document ID or logged in user email is not set.");
      return;
    }

    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/ToggleFavouriteServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ uniqueId: documentId, email: loggedInUserEmail, isFavorited: !isFavorited }),
      headers: { 'X-CSRF-Token': this.get('csrfToken') },
      xhrFields: { withCredentials: true },
      success: (response) => {
        if (response.status === "success") {
          this.set('isFavorited', !isFavorited);
        } else {
          console.warn("Failed to toggle favorite status:", response.message);
        }
      },
      error: (xhr, status, error) => {
        console.error("AJAX Error:", status, error);
      }
    });
  },

  checkDocumentExists(documentId) {
    return new Promise((resolve) => {
      const loggedInUserEmail = this.get('loggedInUserEmail');
      if (!loggedInUserEmail) {
        console.error("Logged in user email is not set.");
        resolve(false);
        return;
      }

      const requestData = {
        uniqueId: documentId,
        email: loggedInUserEmail
      };

      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/GetDocumentDetailsServlet',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(requestData),
        headers: { 'X-CSRF-Token': this.get('csrfToken') },
        xhrFields: { withCredentials: true },
        success: (response) => {
          if (response.status === "success") {
            this.set('isViewer', response.role.toLowerCase() === 'viewer');
            this.set('isOwnerOrEditor', response.role.toLowerCase() === 'owner' || response.role.toLowerCase() === 'editor');
            resolve(true);
          } else {
            resolve(false);
          }
        },
        error: () => {
          resolve(false);
        }
      });
    }).then((exists) => {
      if (!exists) {
        this.transitionToRoute('document-not-found');
      }
      return exists;
    });
  },

  validateSession() {
    return new Promise((resolve, reject) => {
      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/SessionValidationServlet',
        type: 'GET',
        headers: {
          'X-CSRF-Token': this.get('csrfToken')
        },
        xhrFields: { withCredentials: true },
        success: (response) => {
          if (response.status !== "success") {
            this.transitionToRoute('login');
            reject("Session validation failed.");
          } else {
            this.set('loggedInUserEmail', response.email);
            resolve();
          }
        },
        error: () => {
          this.transitionToRoute('login');
          reject("Session validation error.");
        }
      });
    });
  },

  loadDocumentDetails(uniqueId) {
    const loggedInUserEmail = this.get('loggedInUserEmail');
    if (!loggedInUserEmail) {
      console.error("Logged in user email is not set.");
      return;
    }

    const requestData = {
      uniqueId: uniqueId,
      email: loggedInUserEmail
    };

    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/GetDocumentDetailsServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify(requestData),
      headers: { 'X-CSRF-Token': this.get('csrfToken') },
      xhrFields: { withCredentials: true },
      success: (response) => {
        if (response.status === "success") {
          this.set('documentTitle', response.title);
          this.loadDocumentContent(uniqueId);
        } else {
          console.warn("Failed to load document details:", response.message);
        }
      },
      error: (xhr, status, error) => {
        console.error("AJAX Error:", status, error);
      }
    });
  },

  loadDocumentContent(uniqueId) {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/GetDocumentContentServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ uniqueId }),
      headers: { 'X-CSRF-Token': this.get('csrfToken') },
      xhrFields: { withCredentials: true },
      success: (response) => {
        if (response.status === "success") {
          this.set('documentContent', response.content);
          this.set('lastSavedContent', response.content); // Initialize last saved content
        } else {
          console.warn("Failed to load document content:", response.message);
        }
      },
      error: (xhr, status, error) => {
        console.error("AJAX Error:", status, error);
      }
    });
  },

  fetchAllUsers() {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/GetAllUsersServlet',
      type: 'GET',
      headers: { 'X-CSRF-Token': this.get('csrfToken') },
      xhrFields: { withCredentials: true },
      success: (response) => {
        if (response.status === "success") {
          this.set('allUsers', response.users);
        } else {
          console.warn("Failed to fetch users:", response.message);
        }
      },
      error: (xhr, status, error) => {
        console.error("AJAX Error:", status, error);
      }
    });
  },

  saveDocumentContent() {
    const documentId = this.get('documentId');
    const content = Ember.$('.document-editor').html();

    if (documentId && content && this.get('isOwnerOrEditor')) {
      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/SaveDocumentContentServlet',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ uniqueId: documentId, content }),
        headers: { 'X-CSRF-Token': this.get('csrfToken') },
        xhrFields: { withCredentials: true },
        success: (response) => {
          if (response.status === "success") {
            const lastUpdated = new Date(response.last_updated);
            this.updateLastUpdatedMessage(lastUpdated);
            this.set('lastSavedContent', content); // Update last saved content
          } else {
            console.warn("Failed to save document content:", response.message);
            this.set('lastUpdatedMessage', "Failed to save document.");
          }
        },
        error: (xhr, status, error) => {
          console.error("AJAX Error:", status, error);
          this.set('lastUpdatedMessage', "Error saving document.");
        }
      });
    }
  },

  updateLastUpdatedMessage(lastUpdated) {
    const now = new Date();
    const diffInSeconds = Math.floor((now - lastUpdated) / 1000);

    if (diffInSeconds < 60) {
      this.set('lastUpdatedMessage', "Document saved successfully.");
    } else {
      const diffInMinutes = Math.floor(diffInSeconds / 60);
      const diffInHours = Math.floor(diffInMinutes / 60);
      const diffInDays = Math.floor(diffInHours / 24);

      if (diffInDays > 0) {
        this.set('lastUpdatedMessage', `Document updated ${diffInDays} day(s) ago.`);
      } else if (diffInHours > 0) {
        this.set('lastUpdatedMessage', `Document updated ${diffInHours} hour(s) ago.`);
      } else {
        this.set('lastUpdatedMessage', `Document updated ${diffInMinutes} minute(s) ago.`);
      }
    }
  },

  initWebSocket(documentId) {
    const socket = new WebSocket(`ws://localhost:8080/DocsApp_war_exploded/ws/${documentId}`);

    socket.onopen = () => {
      console.log("WebSocket connection established.");
    };

    socket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'contentUpdate') {
        this.applyDeltaUpdate(data.content);
      } else if (data.type === 'titleUpdate') {
        this.set('documentTitle', data.title);
      } else if (data.type === 'initialContent') {
        this.set('documentContent', data.content);
        this.set('lastSavedContent', data.content);
        this.updateEditorContent(data.content);
      }
    };

    socket.onclose = () => {
      console.log("WebSocket connection closed.");
    };

    this.set('socket', socket);
  },

  applyDeltaUpdate(delta) {
    const currentContent = this.get('documentContent');
    console.log("Current Content:", currentContent);
    console.log("Delta:", delta);

    let updatedContent = currentContent;

    if (delta) {
      switch (delta.operation) {
        case 'ADD':
          updatedContent = this.insertContent(currentContent, delta.position, delta.content);
          break;
        case 'SUB':
          updatedContent = this.deleteContent(currentContent, delta.position, delta.content);
          break;
        case 'REPLACE':
          updatedContent = this.replaceContent(currentContent, delta.position, delta.oldContent, delta.content);
          break;
        default:
          console.warn("Unknown delta operation:", delta.operation);
          return;
      }
      console.log("Updated Content:", updatedContent);
      this.set('documentContent', updatedContent);
      this.updateEditorContent(updatedContent);
    }
  },

  updateEditorContent(content) {
    Ember.run.next(() => {
      const editor = Ember.$('.document-editor');
      if (editor.html() !== content) {
        editor.html(content);
      }
    });
  },


  insertContent(content, position, newContent) {
    return content.slice(0, position) + newContent + content.slice(position);
  },

  deleteContent(content, position, length) {
    return content.slice(0, position) + content.slice(position + length);
  },

  replaceContent(content, position, oldContent, newContent) {
    return content.slice(0, position) + newContent + content.slice(position + oldContent.length);
  },



  sendContentUpdate(content) {
    const socket = this.get('socket');
    if (socket && socket.readyState === WebSocket.OPEN && this.get('isOwnerOrEditor')) {
      const delta = this.calculateDelta(this.get('lastSavedContent'), content);
      if (delta) {
        socket.send(JSON.stringify({
          type: 'contentUpdate',
          content: delta
        }));
        this.set('lastSavedContent', content);
      }
    }
  },


  calculateDelta(oldContent, newContent) {
    if (!oldContent || !newContent) {
      return newContent;
    }

    let start = 0;
    while (start < oldContent.length && start < newContent.length && oldContent[start] === newContent[start]) {
      start++;
    }

    let endOld = oldContent.length - 1;
    let endNew = newContent.length - 1;
    while (endOld >= start && endNew >= start && oldContent[endOld] === newContent[endNew]) {
      endOld--;
      endNew--;
    }

    if (endOld < start && endNew < start) {
      return null;
    } else if (endOld < start) {
      return {
        operation: 'ADD',
        position: start,
        content: newContent.substring(start, endNew + 1)
      };
    } else if (endNew < start) {
      return {
        operation: 'SUB',
        position: start,
        content: oldContent.substring(start, endOld + 1).length
      };
    } else {
      return {
        operation: 'REPLACE',
        position: start,
        content: newContent.substring(start, endNew + 1),
        oldContent: oldContent.substring(start, endOld + 1)
      };
    }
  },

  sendTitleUpdate(title) {
    const socket = this.get('socket');
    if (socket && socket.readyState === WebSocket.OPEN && this.get('isOwnerOrEditor')) {
      socket.send(JSON.stringify({
        type: 'titleUpdate',
        title: title
      }));
    }
  },

  actions: {

    toggleFavorite() {
      this.toggleFavorite();
    },

    trackTitleChange() {
      let title = Ember.$(".document-title").text().trim();
      this.set('updatedTitle', title);
    },

    updateTitle() {
      if (!this.get('isOwnerOrEditor')) {
        alert("You do not have permission to edit this document.");
        return;
      }

      let title = this.get('updatedTitle');
      let uniqueId = this.get('documentId');

      if (!title || !uniqueId) {
        return;
      }

      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/UpdateDocumentTitleServlet',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ uniqueId, title }),
        headers: { 'X-CSRF-Token': this.get('csrfToken') },
        xhrFields: { withCredentials: true },
        success: (response) => {
          if (response.status !== "success") {
            alert("Error updating document title.");
          } else {
            this.set('documentTitle', title);
            this.sendTitleUpdate(title);
          }
        },
        error: (xhr, status, error) => {
          console.error("AJAX Error:", status, error);
          alert("Error updating document title.");
        }
      });
    },

    handleEmailInput(event) {
      const inputValue = event.target.value.trim().toLowerCase();
      if (inputValue.includes('@')) {
        const loggedInUserEmail = this.get('loggedInUserEmail');
        const filteredUsers = this.get('allUsers').filter(user =>
          user.toLowerCase().includes(inputValue) && user !== loggedInUserEmail
        );

        this.set('filteredUsers', filteredUsers);
        Ember.$('#email-dropdown').show();
      } else {
        this.set('filteredUsers', []);
        Ember.$('#email-dropdown').hide();
      }
    },

    selectEmail(email) {
      Ember.$('#email').val(email);
      Ember.$('#email-dropdown').hide();
    },

    shareDocument() {
      if (!this.get('isOwnerOrEditor')) {
        alert("You do not have permission to share this document.");
        return;
      }

      const email = Ember.$('#email').val().trim();
      const accessLevel = Ember.$('.access-level').val();
      const documentId = this.get('documentId');

      if (!email || !documentId) {
        alert("Please enter a valid email and ensure the document is loaded.");
        return;
      }

      Ember.$.ajax({
        url: 'http://localhost:8080/DocsApp_war_exploded/UpdatePermissionsServlet',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({ email, documentId, role: accessLevel }),
        headers: { 'X-CSRF-Token': this.get('csrfToken') },
        xhrFields: { withCredentials: true },
        success: (response) => {
          if (response.status === "success") {
            // Send email to the user
            this.sendShareEmail(email, documentId, accessLevel);

            Ember.$('#success-popup').show();
            Ember.$('#share-popup').hide();
            Ember.$('#email').val('');
            Ember.$('.access-level').val('Viewer');
            setTimeout(() => {
              Ember.$('#success-popup').hide();
            }, 2000);
          } else {
            alert("Error sharing document: " + response.message);
          }
        },
        error: (xhr, status, error) => {
          console.error("AJAX Error:", status, error);
          alert("Error sharing document.");
        }
      });
    },

    updateContent() {
      if (!this.get('isOwnerOrEditor')) {
        alert("You do not have permission to edit this document.");
        return;
      }

      const content = Ember.$('.document-editor').html();
      this.sendContentUpdate(content);
    },

    saveDocument() {
      this.saveDocumentContent();
    },

    saveAsWord() {
      const content = this.get('documentContent');
      const title = this.get('documentTitle') || 'Untitled';
      const htmlContent = `
        <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word" xmlns="http://www.w3.org/TR/REC-html40">
          <head>
            <meta charset="UTF-8">
            <title>${title}</title>
          </head>
          <body>
            ${content}
          </body>
        </html>
      `;

      const blob = new Blob([htmlContent], { type: 'application/msword' });
      const url = URL.createObjectURL(blob);

      const link = document.createElement('a');
      link.href = url;
      link.download = `${title}.doc`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    }
  },

  sendShareEmail(email, documentId, accessLevel) {
    Ember.$.ajax({
      url: 'http://localhost:8080/DocsApp_war_exploded/SendShareEmailServlet',
      type: 'POST',
      contentType: 'application/json',
      data: JSON.stringify({ email, documentId, accessLevel }),
      headers: { 'X-CSRF-Token': this.get('csrfToken') },
      xhrFields: { withCredentials: true },
      success: (response) => {
        if (response.status !== "success") {
          console.warn("Failed to send email:", response.message);
        }
      },
      error: (xhr, status, error) => {
        console.error("AJAX Error:", status, error);
      }
    });
  }

});
