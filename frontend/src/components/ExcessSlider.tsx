const ALLOWED = [0, 100, 250, 500, 750, 1000];

export function ExcessSlider({
  value,
  onChange,
}: {
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <label style={{ display: "block", margin: "8px 0" }}>
      Voluntary excess: <strong>£{value}</strong>
      <input
        type="range"
        min={0}
        max={ALLOWED.length - 1}
        step={1}
        value={ALLOWED.indexOf(value)}
        onChange={(e) => onChange(ALLOWED[Number(e.target.value)])}
        style={{ width: "100%" }}
      />
    </label>
  );
}
