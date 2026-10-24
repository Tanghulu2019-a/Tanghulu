"""Small NBT decoder for comparing official structure templates in developer QA."""
import gzip
import io
import struct

def decode(data):
    stream=io.BytesIO(gzip.decompress(data))
    def read(fmt): return struct.unpack('>'+fmt,stream.read(struct.calcsize('>'+fmt)))[0]
    def string(): return stream.read(read('H')).decode('utf-8')
    def payload(kind):
        if kind in (1,2,3,4,5,6): return read({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7: return list(stream.read(read('i')))
        if kind==8: return string()
        if kind==9:
            child=read('b'); count=read('i'); return [payload(child) for _ in range(count)]
        if kind==10:
            result={}
            while True:
                child=read('b')
                if child==0: return result
                name=string(); result[name]=payload(child)
        if kind in (11,12): return [read('i' if kind==11 else 'q') for _ in range(read('i'))]
        raise ValueError(kind)
    kind=read('b'); string(); return payload(kind)

