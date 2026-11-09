import { useEffect, useRef, useState } from 'react';
import { Input } from './base/input/input';
import { Label } from './base/input/label';
import { NativeSelect } from './base/select/select-native';

// RequiredMark kept for CHECKBOX's own fieldset legend in QuestionField.jsx, which has no
// Untitled UI component to delegate to (no CheckboxGroup ships in the free tier).
export function RequiredMark({ required }) {
  if (!required) return null;
  return <span className="ml-1 text-error-primary">*</span>;
}

// TEXT/NUMBER/DATE are driven by a local, controlled value so that typing feels normal (no
// remount-on-every-keystroke), but still pick up a value change that came from elsewhere - e.g.
// another answer on this same template cascading to invalidate this one - as long as the field
// isn't the one currently focused. A focused field is never clobbered mid-edit by its own
// just-submitted value echoing back from the refetch.
export function useSyncedValue(externalValue) {
  const [localValue, setLocalValue] = useState(externalValue ?? '');
  const inputRef = useRef(null);

  useEffect(() => {
    if (document.activeElement !== inputRef.current) {
      setLocalValue(externalValue ?? '');
    }
  }, [externalValue]);

  return [localValue, setLocalValue, inputRef];
}

export function TextField({ fieldId, label, value, required, validation, onChange }) {
  const [localValue, setLocalValue, inputRef] = useSyncedValue(value);
  return (
    <div className="mb-4">
      <Input
        ref={inputRef}
        id={fieldId}
        label={label}
        isRequired={required}
        value={localValue}
        pattern={validation?.pattern}
        maxLength={validation?.maxLength}
        minLength={validation?.minLength}
        onChange={(value) => {
          setLocalValue(value);
          onChange(value);
        }}
      />
    </div>
  );
}

export function NumberField({ fieldId, label, value, required, validation, onChange }) {
  const [localValue, setLocalValue, inputRef] = useSyncedValue(value);
  return (
    <div className="mb-4">
      <Input
        ref={inputRef}
        id={fieldId}
        type="number"
        label={label}
        isRequired={required}
        value={localValue}
        min={validation?.min}
        max={validation?.max}
        onChange={(value) => {
          setLocalValue(value);
          onChange(value === '' ? null : Number(value));
        }}
      />
    </div>
  );
}

// Kept as a styled native <input type="date"> rather than Untitled UI's own DatePicker - that
// component is popover+calendar only (a Button trigger, no visible text input, a hardcoded
// "Date picker" aria-label), a materially different interaction model from "type a date". Its own
// value type (@internationalized/date's CalendarDate) would also need converting to/from the plain
// ISO string this whole app stores and passes to calculateAge() - deferred rather than taking that
// on inside what's meant to be a re-skin. Styled with the same ring/bg/radius tokens Input uses so
// it still reads as part of the same design system.
export function DateField({ fieldId, label, value, required, onChange }) {
  const [localValue, setLocalValue, inputRef] = useSyncedValue(value);
  return (
    <div className="mb-4">
      {label && (
        <Label htmlFor={fieldId} isRequired={required} className="mb-1.5">
          {label}
        </Label>
      )}
      <input
        ref={inputRef}
        id={fieldId}
        type="date"
        value={localValue}
        aria-required={required}
        onChange={(e) => {
          setLocalValue(e.target.value);
          onChange(e.target.value);
        }}
        className="w-full rounded-lg bg-primary px-3 py-2 text-md text-primary shadow-xs ring-1 ring-primary ring-inset outline-hidden focus-visible:ring-2 focus-visible:ring-brand"
      />
    </div>
  );
}

export function DropdownField({ fieldId, label, value, required, options, onChange }) {
  return (
    <div className="mb-4">
      {/* NativeSelect's own vendored <Label> call never passes isRequired through, so its asterisk
          is gated on a `group-required:` ancestor that doesn't exist here - force it visible
          ourselves rather than edit the vendored file. */}
      <NativeSelect
        id={fieldId}
        label={label}
        required={required}
        className={required ? '[&_[data-label]>span]:block' : undefined}
        value={value ?? ''}
        options={[{ label: 'Select...', value: '', disabled: true }, ...(options ?? [])]}
        onChange={(e) => {
          const selected = (options ?? []).find((option) => String(option.value) === e.target.value);
          onChange(selected ? selected.value : e.target.value);
        }}
      />
    </div>
  );
}
