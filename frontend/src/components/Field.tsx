interface FieldOption {
  value: string;
  label: string;
}

interface FieldProps {
  label: string;
  name: string;
  type?: 'text' | 'email' | 'number' | 'select';
  value: string;
  onChange: (value: string) => void;
  options?: FieldOption[];
  required?: boolean;
  placeholder?: string;
  step?: string;
}

export default function Field({
  label,
  name,
  type = 'text',
  value,
  onChange,
  options,
  required,
  placeholder,
  step,
}: FieldProps) {
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      {type === 'select' ? (
        <select
          id={name}
          name={name}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={required}
        >
          {options?.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      ) : (
        <input
          id={name}
          name={name}
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={required}
          placeholder={placeholder}
          step={step}
        />
      )}
    </div>
  );
}
