"""Reads the OSRS Wiki's Module:Skill calc data pages: a Lua `return { ... }` of plain tables, no code."""
import re

TOKEN = re.compile(r"""\s+|--\[\[.*?\]\]|--[^\n]*|(?P<str>'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")|(?P<num>-?\d+(?:\.\d+)?)"""
                   r"""|(?P<name>[A-Za-z_]\w*)|(?P<sym>[{}=,;\[\]])|(?P<op>[-+*/%^().])""", re.S)


def tokens(text):
    pos = 0
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if not m:
            raise ValueError("unexpected %r at %d" % (text[pos:pos + 20], pos))
        pos = m.end()
        if m.lastgroup == "str":
            yield "str", re.sub(r"\\(.)", r"\1", m.group()[1:-1])
        elif m.lastgroup:
            yield m.lastgroup, m.group()


def parse(text):
    toks = list(tokens(text))
    if not toks or toks[0] != ("name", "return"):
        raise ValueError("not a data module")
    value, end = _value(toks, 1)
    return value


def _value(t, i):
    if t[i][0] == "op" or (t[i][0] == "num" and t[i + 1][0] == "op"):
        # arithmetic like (2/16 / 45): numbers only, so skip it to the next field
        while t[i][0] in ("op", "num"):
            i += 1
        return None, i
    kind, v = t[i]
    if kind == "str":
        return v, i + 1
    if kind == "num":
        return float(v) if "." in v else int(v), i + 1
    if kind == "name":
        if v in ("true", "false", "nil"):
            return {"true": True, "false": False, "nil": None}[v], i + 1
        raise ValueError("code, not data: %s" % v)
    if v != "{":
        raise ValueError("unexpected %s" % v)
    i += 1
    items, fields = [], {}
    while t[i][1] != "}":
        if t[i][0] == "name" and t[i + 1][1] == "=":
            key, i = t[i][1], i + 2
            fields[key], i = _value(t, i)
        elif t[i][1] == "[":
            key, i = _value(t, i + 1)
            i += 2  # "]" "="
            fields[key], i = _value(t, i)
        else:
            v, i = _value(t, i)
            items.append(v)
        if t[i][1] in (",", ";"):
            i += 1
    return (fields if fields or not items else items) if not (fields and items) else dict(fields, _items=items), i + 1
