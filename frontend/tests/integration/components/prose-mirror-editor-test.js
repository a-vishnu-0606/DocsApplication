import { moduleForComponent, test } from 'ember-qunit';
import hbs from 'htmlbars-inline-precompile';

moduleForComponent('prose-mirror-editor', 'Integration | Component | prose mirror editor', {
  integration: true
});

test('it renders', function(assert) {
  
  // Set any properties with this.set('myProperty', 'value');
  // Handle any actions with this.on('myAction', function(val) { ... });" + EOL + EOL +

  this.render(hbs`{{prose-mirror-editor}}`);

  assert.equal(this.$().text().trim(), '');

  // Template block usage:" + EOL +
  this.render(hbs`
    {{#prose-mirror-editor}}
      template block text
    {{/prose-mirror-editor}}
  `);

  assert.equal(this.$().text().trim(), 'template block text');
});
