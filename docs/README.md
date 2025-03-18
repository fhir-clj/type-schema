# Open questions

- `self-reference` -- looks terrible. Put inside root? (identifier/base)
- What is an uniq id for type? (identifier)
  - name -- possible collisions between different packages
  - canonical -- no version
  - [package + ] canonical + version
- binding interpretation:
  - for code -- `"c"`
  - for CodableConcept `{"coding": [{"code": "c", "system": "s"}]}`
- Do we actually need to separate resource/complex type?
  - From data type perspective -- no.
  - From some special cases like ORM -- maybe. E.g. -- how to normilize the data?
