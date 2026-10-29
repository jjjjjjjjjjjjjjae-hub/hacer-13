import base64,importlib.util,json,pathlib,tempfile,unittest,zipfile
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding,PrivateFormat,NoEncryption
ROOT=pathlib.Path(__file__).resolve().parents[1]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
inspect=module('inspect_xapk','LAC-tools/inspect_xapk.py');publish=module('publish_data','tools/publish_data.py')

class ToolTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=pathlib.Path(self.tmp.name)
    def archive(self,extra=None,obb='Android/obb/com.MA.LAC/main.60.com.MA.LAC.obb'):
        p=self.root/'test.xapk'
        with zipfile.ZipFile(p,'w') as z:
            z.writestr('com.MA.LAC-60.apk',b'fixture');z.writestr(obb,b'obb-fixture')
            if extra:z.writestr(extra,b'payload')
        return p
    def test_structure_only_never_claims_signature_or_runtime(self):
        r=inspect.audit(self.archive());self.assertEqual(r['status'],'structure-only');self.assertFalse(r['runtime_tested']);self.assertNotIn('signature_verified',r);self.assertTrue(r['crc_verified'])
    def test_traversal_rejected(self):
        with self.assertRaises(ValueError):inspect.audit(self.archive('../escape'))
    def test_absolute_path_rejected(self):
        with self.assertRaises(ValueError):inspect.audit(self.archive('/escape'))
    def test_duplicate_entry_rejected(self):
        with self.assertRaises(ValueError):inspect.audit(self.archive('com.MA.LAC-60.apk'))
    def test_invalid_obb_package_rejected(self):
        with self.assertRaises(ValueError):inspect.audit(self.archive(obb='Android/obb/com.wrong/main.60.com.MA.LAC.obb'))
    def test_split_apks_rejected(self):
        with self.assertRaises(ValueError):inspect.audit(self.archive('split.apk'))
    def source(self):
        src=self.root/'data';src.mkdir();(src/'menu.json').write_bytes((ROOT/'updates/lac/data/menu.json').read_bytes());return src
    def test_sign_and_verify_manifest(self):
        key=Ed25519PrivateKey.generate();p=self.root/'private.pem';p.write_bytes(key.private_bytes(Encoding.PEM,PrivateFormat.PKCS8,NoEncryption()))
        out=self.root/'out';m=publish.publish(p,self.source(),out,7);env=json.loads((out/'release-v2.json').read_text());payload=base64.b64decode(env['payload_b64']);key.public_key().verify(base64.b64decode(env['signature_b64']),payload)
        self.assertEqual(json.loads(payload)['revision'],7);self.assertEqual(m['files'][0]['size'],len((out/'blobs'/m['files'][0]['sha256']).read_bytes()))
    def test_reject_executable_data(self):
        with self.assertRaises(ValueError):publish.validate('plugin.dex',b'payload')
    def test_reject_unknown_ui_field(self):
        c=json.loads((ROOT/'updates/lac/data/menu.json').read_text());c['execute']='command'
        with self.assertRaises(ValueError):publish.validate('menu.json',json.dumps(c).encode())
    def test_reject_non_demo(self):
        c=json.loads((ROOT/'updates/lac/data/menu.json').read_text());c['features'][0]['kind']='native'
        with self.assertRaises(ValueError):publish.validate('menu.json',json.dumps(c).encode())
    def test_png_dimension_limit(self):
        data=b'\x89PNG\r\n\x1a\n'+b'\0\0\0\rIHDR'+(99999).to_bytes(4,'big')+(1).to_bytes(4,'big')+bytes(9)
        with self.assertRaises(ValueError):publish.validate('resources/badge.png',data)
    def test_published_manifest_is_valid(self):
        from cryptography.hazmat.primitives.serialization import load_der_public_key
        env=json.loads((ROOT/'updates/lac/release-v2.json').read_text());key=load_der_public_key(base64.b64decode((ROOT/'updates/lac/public-key.spki.b64').read_text()));raw=base64.b64decode(env['payload_b64']);key.verify(base64.b64decode(env['signature_b64']),raw)
        for f in json.loads(raw)['files']:
            import hashlib
            data=(ROOT/'updates/lac/blobs'/f['sha256']).read_bytes();self.assertEqual(len(data),f['size']);self.assertEqual(hashlib.sha256(data).hexdigest(),f['sha256']);publish.validate(f['path'],data)
