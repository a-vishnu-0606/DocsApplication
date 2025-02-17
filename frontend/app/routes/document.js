import Ember from 'ember';

export default Ember.Route.extend({
  model(params) {
    return {
      document_id: params.document_id
    };
  },

  actions: {
    goBack() {
      this.transitionTo('dashboard');
    },
    openSharePopup() {
      document.getElementById('share-popup').style.display = 'block';
    },
    closeSharePopup() {
      document.getElementById('share-popup').style.display = 'none';
    },
    toggleMenu() {
      document.getElementById('sidebar').classList.toggle('open');
    }
  },

  setupController(controller, model) {
    this._super(controller, model);

    Ember.run.scheduleOnce('afterRender', this, this.initializeDocumentEditor);
  },

  initializeDocumentEditor() {
    const editor = document.querySelector('.document-editor');
    const container = document.querySelector('.document-container');
    const sidebar = document.getElementById('sidebar1');
    const menuToggle = document.getElementById('menu-toggle');

    if (menuToggle) {
      menuToggle.addEventListener('click', function () {
        sidebar.classList.toggle('open');

        if (sidebar.classList.contains('open')) {
          menuToggle.innerHTML = '&times;';
        } else {
          menuToggle.innerHTML = '☰';
        }
      });
    }

    const closeSidebarButton = document.getElementById('close-sidebar');
    if (closeSidebarButton) {
      closeSidebarButton.addEventListener('click', function () {
        sidebar.classList.remove('open');
        menuToggle.innerHTML = '☰';
      });
    }

    const fontStyle = document.getElementById('font-style');
    if (fontStyle) {
      fontStyle.addEventListener('change', function () {
        editor.style.fontFamily = this.value;
      });
    }

    const fontColor = document.getElementById('font-color');
    if (fontColor) {
      fontColor.addEventListener('input', function () {
        editor.style.color = this.value;
      });
    }

    const fontSize = document.getElementById('font-size');
    if (fontSize) {
      fontSize.addEventListener('input', function () {
        editor.style.fontSize = this.value + 'px';
      });
    }

    const alignmentButtons = document.querySelectorAll('.alignment-buttons button');
    if (alignmentButtons) {
      alignmentButtons.forEach(button => {
        button.addEventListener('click', function () {
          editor.style.textAlign = this.getAttribute('data-align');
        });
      });
    }

    const spacing = document.getElementById('spacing');
    if (spacing) {
      spacing.addEventListener('input', function () {
        editor.style.lineHeight = this.value + 'px';
      });
    }

    if (editor) {
      editor.addEventListener('input', function () {
        checkOverflow(editor);
      });
    }

    function checkOverflow(element) {
      if (element.scrollHeight > element.clientHeight) {
        createNewPage();
      }
    }

    function createNewPage() {
      const newPage = document.createElement('div');
      newPage.className = 'document-editor new-page';
      newPage.setAttribute('contenteditable', 'true');
      container.appendChild(newPage);
      newPage.focus();
    }
  }
});
