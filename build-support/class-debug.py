"""Remove optional local-variable debug tables; retain bytecode, line numbers and all other data."""
import struct


def compact(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Invalid class file')
    names = {}
    offset = 10
    count = struct.unpack_from('>H', data, 8)[0]
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, offset)[0]
            names[index] = data[offset + 2:offset + 2 + length]
            offset += length + 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            offset += 2
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f'Unknown constant pool tag {tag}')
        index += 1

    def attributes(position, local_tables=False):
        size = struct.unpack_from('>H', data, position)[0]
        position += 2
        result = []
        for _ in range(size):
            name, length = struct.unpack_from('>HI', data, position)
            start, end = position + 6, position + 6 + length
            if end > len(data):
                raise ValueError('Truncated attribute')
            kind = names[name]
            if local_tables and kind in (b'LocalVariableTable', b'LocalVariableTypeTable'):
                pass
            elif kind == b'Code' and not local_tables:
                code_length = struct.unpack_from('>I', data, start + 4)[0]
                exception_offset = start + 8 + code_length
                exceptions = struct.unpack_from('>H', data, exception_offset)[0]
                nested_offset = exception_offset + 2 + exceptions * 8
                nested, nested_end = attributes(nested_offset, True)
                if nested_end != end:
                    raise ValueError('Invalid Code attribute length')
                payload = data[start:nested_offset] + nested
                result.append(struct.pack('>HI', name, len(payload)) + payload)
            else:
                result.append(data[position:end])
            position = end
        return struct.pack('>H', len(result)) + b''.join(result), position

    # Preserve the complete constant pool, class header and interfaces verbatim.
    interfaces = struct.unpack_from('>H', data, offset + 6)[0]
    position = offset + 8 + interfaces * 2
    output = [data[:position]]
    for _ in range(2):  # Fields and methods, including all non-Code attributes.
        members = struct.unpack_from('>H', data, position)[0]
        output.append(data[position:position + 2])
        position += 2
        for _ in range(members):
            output.append(data[position:position + 6])
            attrs, position = attributes(position + 6)
            output.append(attrs)
    attrs, position = attributes(position)
    output.append(attrs)
    if position != len(data):
        raise ValueError('Unexpected class trailing bytes')
    return b''.join(output)
