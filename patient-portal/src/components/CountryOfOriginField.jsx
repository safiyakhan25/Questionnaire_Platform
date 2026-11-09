import { useListData } from 'react-stately';
import { TagSelect } from './base/select/tag-select';

// `TagSelect` manages its own selection internally through the `useListData` instance it's given
// (it calls `selectedItems.append`/`.remove` itself on insert/clear - see tag-select.tsx), so
// `initialItems` only needs to be right once, at mount: the `value` prop coming back from this
// field's own onChange→backend→refresh round trip never needs to be re-synced into it. A subject
// switch (a different family member, a different template) already gets a fresh mount for free
// from TemplateView's own `template` reset to null, which unmounts this component entirely - no
// extra remount-on-value-change is needed here, and one would cost real UX: keying this component
// on `value` made it remount (and drop focus out of the search input) after every single
// selection, since a selection changes `value` by definition.
export default function CountryOfOriginField({ fieldId, label, value, required, options, onChange }) {
  const selected = value ?? [];
  const items = (options ?? []).map((option) => ({ id: option.value, label: option.label }));

  const selectedItems = useListData({
    initialItems: items.filter((item) => selected.includes(item.id)),
  });

  return (
    <div className="mb-4">
      <TagSelect
        id={fieldId}
        label={label}
        isRequired={required}
        items={items}
        selectedItems={selectedItems}
        onItemInserted={(key) => onChange([...selected, key])}
        onItemCleared={(key) => onChange(selected.filter((v) => v !== key))}
      >
        {(item) => <TagSelect.Item key={item.id} id={item.id} label={item.label} />}
      </TagSelect>
    </div>
  );
}
