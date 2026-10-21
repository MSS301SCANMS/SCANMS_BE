import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import net from 'node:net';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const work = path.join(repo, '.local', 'fashion-seed-v1');
const readEnv = file => fs.existsSync(file) ? Object.fromEntries(fs.readFileSync(file, 'utf8').split(/\r?\n/)
  .filter(line => /^\s*[^#=]+=/.test(line)).map(line => {
    const i = line.indexOf('=');
    return [line.slice(0, i).trim(), line.slice(i + 1).trim().replace(/^['"]|['"]$/g, '')];
  })) : {};
const env = { ...readEnv(path.join(repo, '.env')), ...readEnv(path.join(repo, 'product-service', '.env')), ...process.env };
const id = key => {
  const bytes = createHash('sha256').update(`scanms-fashion-demo-v1:${key}`).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 15) | 80; bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
const shops = [
  ['moc-basics', 'Mộc Basics', 'MB', '#23443b', 'Trang phục cơ bản, dễ phối mỗi ngày.'],
  ['nep-studio', 'Nếp Studio', 'NS', '#8c4e57', 'Váy và trang phục nữ thanh lịch.'],
  ['pho-streetwear', 'Phố Streetwear', 'PS', '#283952', 'Sơ mi, áo thun và áo khoác phong cách đường phố.'],
  ['lua-atelier', 'Lụa Atelier', 'LA', '#8c7048', 'Những thiết kế nữ dành cho ngày thường và dịp đặc biệt.'],
];
const categories = {
  'ao-so-mi': 'Áo sơ mi', 'ao-thun': 'Áo thun', 'ao-khoac': 'Áo khoác',
  'dam-vay': 'Đầm & váy', 'bo-trang-phuc': 'Bộ trang phục', 'ao-the-thao': 'Áo thể thao',
};
// Source IDs are pinned. Prices, inventory and shop names are fictional local demo data.
const products = [
  ['d',83,'Sơ mi caro xanh đen','ao-so-mi',0,329000,'Xanh đen'],
  ['d',84,'Áo thun họa tiết gaming','ao-thun',0,229000,'Trắng'],
  ['d',85,'Sơ mi caro phong cách casual','ao-so-mi',2,349000,'Caro'],
  ['d',86,'Sơ mi nam tay ngắn','ao-so-mi',0,299000,'Phối màu'],
  ['d',87,'Sơ mi caro nam dài tay','ao-so-mi',2,359000,'Caro'],
  ['d',177,'Đầm maxi họa tiết hoa','dam-vay',3,899000,'Họa tiết hoa'],
  ['d',178,'Bộ corset phối chân váy xòe','bo-trang-phuc',3,749000,'Đỏ'],
  ['d',179,'Bộ corset phối chân váy đen','bo-trang-phuc',3,699000,'Đen'],
  ['d',180,'Đầm nữ dáng thanh lịch','dam-vay',1,459000,'Theo ảnh'],
  ['d',181,'Bộ trang phục đỏ đen','bo-trang-phuc',3,799000,'Đỏ đen'],
  ['d',162,'Đầm xanh dáng xòe','dam-vay',1,399000,'Xanh'],
  ['d',163,'Đầm nữ mùa hè','dam-vay',1,429000,'Theo ảnh'],
  ['d',164,'Đầm xám hằng ngày','dam-vay',1,389000,'Xám'],
  ['d',165,'Đầm ngắn trẻ trung','dam-vay',1,379000,'Theo ảnh'],
  ['d',166,'Đầm caro tartan','dam-vay',3,469000,'Caro'],
  ['f',2,'Áo nam raglan phối màu','ao-thun',0,249000,'Trắng đen'],
  ['f',3,'Áo khoác nam cổ đứng','ao-khoac',2,649000,'Nâu'],
  ['f',4,'Áo nam dài tay cổ tròn','ao-thun',0,269000,'Xanh'],
  ['f',15,'Áo khoác nữ giữ ấm','ao-khoac',2,789000,'Tím'],
  ['f',16,'Áo khoác nữ phong cách biker','ao-khoac',2,699000,'Đen'],
  ['f',17,'Áo khoác nữ có mũ','ao-khoac',2,549000,'Xanh'],
  ['f',18,'Áo nữ cổ thuyền tay ngắn','ao-thun',1,199000,'Trắng'],
  ['f',19,'Áo thể thao nữ tay ngắn','ao-the-thao',0,219000,'Đỏ'],
  ['f',20,'Áo thun nữ in chữ','ao-thun',3,189000,'Tím'],
].map(([source, sourceId, name, category, shop, price, color], index) => ({
  key: `${source}-${sourceId}`, source, sourceId, name, category, shop, price, color, index,
}));
const restockArg = process.argv.find(arg => arg.startsWith('--restock='));
const minimumStock = restockArg ? Number(restockArg.split('=')[1]) : null;
if (minimumStock !== null && (!Number.isSafeInteger(minimumStock) || minimumStock < 1 || minimumStock > 100000)) {
  throw new Error('--restock must be an integer between 1 and 100000.');
}
const dbUrl = new URL((env.DB_URL || `jdbc:postgresql://127.0.0.1:${env.PRODUCT_DB_PORT || 5437}/${env.PRODUCT_DB_NAME || 'product_db'}`).replace(/^jdbc:/, ''));
if (!['127.0.0.1', 'localhost', '[::1]'].includes(dbUrl.hostname)) throw new Error('This demo seeder only writes to a local PostgreSQL database.');
const pgBin = env.POSTGRES_BIN || 'C:\\Program Files\\PostgreSQL\\18\\bin';
const pgEnv = { ...process.env, PGPASSWORD: env.DB_PASSWORD || env.POSTGRES_PASSWORD || 'postgres', PGCONNECT_TIMEOUT: '5', PGCLIENTENCODING: 'UTF8' };
const pgArgs = ['-h', dbUrl.hostname, '-p', dbUrl.port || '5432', '-U', env.DB_USERNAME || env.POSTGRES_USER || 'postgres', '-d', decodeURIComponent(dbUrl.pathname.slice(1))];
function pg(tool, args, input) {
  const result = spawnSync(path.join(pgBin, `${tool}.exe`), [...pgArgs, ...args], { env: pgEnv, input, encoding: 'utf8' });
  if (result.error || result.status !== 0) throw new Error(`${tool} failed: ${result.error?.message || result.stderr}`);
  return result.stdout;
}
const sqlString = value => value == null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`;
const json = value => `${sqlString(JSON.stringify(value))}::jsonb`;
async function getJson(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(45000) });
  if (!response.ok) throw new Error(`Source request failed (${response.status}): ${new URL(url).hostname}`);
  return response.json();
}
async function clearCatalogCache() {
  const names = ['stores:all','products:all','public:products','public:stores','public:categories','admin-stats:stores','admin-stats:products'];
  const keys = names.map(name => `scanms:product:cache:v1:${name}::SimpleKey []`);
  await new Promise(resolve => {
    const client = net.createConnection({ host: env.REDIS_HOST || '127.0.0.1', port: Number(env.REDIS_PORT || 6379) });
    client.setTimeout(1500);
    const finish = () => { client.destroy(); resolve(); };
    client.once('connect', () => {
      const parts = ['DEL', ...keys];
      client.write(`*${parts.length}\r\n` + parts.map(s => `$${Buffer.byteLength(s)}\r\n${s}\r\n`).join(''));
    });
    client.once('data', finish); client.once('error', finish); client.once('timeout', finish);
  });
}
async function main() {
  console.log(`Fashion demo: ${shops.length} shops, ${products.length} products, ${products.length * 4} size variants.`);
  if (minimumStock !== null) console.log(`Explicit restock: raise demo variants to at least ${minimumStock} units per size.`);
  console.log(`Local database: ${dbUrl.hostname}:${dbUrl.port || 5432}${dbUrl.pathname}`);
  if (!process.argv.includes('--apply')) { console.log('Preview only. Pass --apply to upload images and seed the local database.'); return; }
  for (const key of ['CLOUDINARY_CLOUD_NAME','CLOUDINARY_API_KEY','CLOUDINARY_API_SECRET']) {
    if (!env[key]) throw new Error(`Missing ${key} in backend .env.`);
  }
  pg('psql', ['-At', '-v', 'ON_ERROR_STOP=1'], "SELECT count(*) FROM products; SELECT count(*) FROM product_variants;");
  fs.mkdirSync(work, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const backup = path.join(work, `before-${stamp}.sql`);
  pg('pg_dump', ['--data-only', '--column-inserts', '--no-owner', '--no-privileges', '--table=stores', '--table=products', '--table=product_categories', '--table=product_images', '--table=product_variants', '--file', backup]);
  console.log('Existing catalog backed up locally.');
  const manifestFile = path.join(work, 'cloudinary-manifest.json');
  const manifest = fs.existsSync(manifestFile) ? JSON.parse(fs.readFileSync(manifestFile, 'utf8')) : {};
  const saveManifest = () => fs.writeFileSync(manifestFile, JSON.stringify(manifest, null, 2));
  async function upload(file, key) {
    const cacheKey = `${env.CLOUDINARY_CLOUD_NAME}/${key}`;
    if (manifest[cacheKey]) return manifest[cacheKey];
    const params = { public_id: `scanms/demo-fashion-v1/${key}`, timestamp: String(Math.floor(Date.now()/1000)), overwrite: 'false' };
    const signature = createHash('sha1').update(Object.keys(params).sort().map(k => `${k}=${params[k]}`).join('&') + env.CLOUDINARY_API_SECRET).digest('hex');
    const form = new FormData();
    for (const [k,v] of Object.entries({ ...params, file, api_key: env.CLOUDINARY_API_KEY, signature })) form.append(k,v);
    const response = await fetch(`https://api.cloudinary.com/v1_1/${encodeURIComponent(env.CLOUDINARY_CLOUD_NAME)}/image/upload`, { method:'POST', body:form, signal:AbortSignal.timeout(90000) });
    const result = await response.json();
    if (!response.ok || !result.secure_url) throw new Error(`Cloudinary upload failed (${response.status}) for ${key}. Check backend credentials and the source image.`);
    manifest[cacheKey] = { url: result.secure_url, publicId: result.public_id, source: file.startsWith('data:') ? 'SCANMS original demo shop lettermark' : file };
    saveManifest();
    return manifest[cacheKey];
  }
  const dummy = new Map();
  for (const category of ['mens-shirts','womens-dresses','tops']) {
    const payload = await getJson(`https://dummyjson.com/products/category/${category}`);
    payload.products.forEach(p => dummy.set(p.id,p));
  }
  const fake = new Map((await getJson('https://fakestoreapi.com/products')).map(p => [p.id,p]));
  const shopLogos = [];
  for (const [key,name,initials,color] of shops) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><rect width="512" height="512" rx="96" fill="${color}"/><rect x="32" y="32" width="448" height="448" rx="76" fill="none" stroke="#e9dbc1" stroke-width="2"/><text x="256" y="284" fill="#f9f4e9" text-anchor="middle" font-family="Georgia,serif" font-size="148">${initials}</text><text x="256" y="366" fill="#e9dbc1" text-anchor="middle" font-family="sans-serif" font-size="22" letter-spacing="6">STUDIO COLLECTION</text></svg>`;
    const asset = await upload(`data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`, `shops/${key}`);
    shopLogos.push(asset.url.replace(/\.svg$/, '.png'));
    console.log(`Shop image ready: ${name}`);
  }
  for (const p of products) {
    const source = (p.source === 'd' ? dummy : fake).get(p.sourceId);
    if (!source) throw new Error(`Source product missing: ${p.key}`);
    p.sourceTitle = source.title;
    p.images = [];
    for (const [index,url] of (p.source === 'd' ? source.images.slice(0,2) : [source.image]).entries()) {
      p.images.push(await upload(url, `products/${p.key}-${index+1}`));
    }
    console.log(`Product images ready: ${p.name}`);
  }
  const sql = ["\\set ON_ERROR_STOP on", 'BEGIN;', "SET LOCAL lock_timeout = '5s';", 'SELECT pg_advisory_xact_lock(624301);'];
  Object.entries(categories).forEach(([slug,name],index) => sql.push(`INSERT INTO product_categories(category_id,name,slug,description,active,display_order,created_at,updated_at) VALUES (${sqlString(id('category:'+slug))},${sqlString(name)},${sqlString('fashion-demo-'+slug)},'Danh mục thời trang mẫu',true,${index},now(),now()) ON CONFLICT DO NOTHING;`));
  shops.forEach(([key,name,,,description],index) => sql.push(`INSERT INTO stores(store_id,owner_user_id,name,description,logo_ref,approval_status,rating_average,rating_count,version,created_at,updated_at) VALUES (${sqlString(id('shop:'+key))},${sqlString(id('owner:'+key))},${sqlString(name)},${sqlString(description+' Gian hàng mẫu SCANMS.')},${sqlString(shopLogos[index])},'APPROVED',0,0,0,now(),now()) ON CONFLICT DO NOTHING;`));
  for (const p of products) {
    const productId = id('product:'+p.key);
    const stock = Object.fromEntries(['S','M','L','XL'].map(size => [size,minimumStock ?? 100]));
    const description = `${p.name}. Mẫu trưng bày để thử giao diện và luồng mua sắm SCANMS; chọn size S–XL. Giá và tồn kho là dữ liệu demo.`;
    sql.push(`INSERT INTO products(product_id,store_id,category_id,name,description,category,material,attributes,image_refs,sizes_stock,size_guide,price_vnd,status,version,created_at,updated_at) VALUES (${sqlString(productId)},${sqlString(id('shop:'+shops[p.shop][0]))},(SELECT category_id FROM product_categories WHERE slug=${sqlString('fashion-demo-'+p.category)}),${sqlString(p.name)},${sqlString(description)},${sqlString(categories[p.category])},'Thông tin chất liệu mẫu',${json({demoSeed:'fashion-v1',color:p.color,source:p.source==='d'?'DummyJSON':'Fake Store API'})},${json(Object.fromEntries(p.images.map((asset,i)=>[i===0?'main':`gallery${i}`,asset.url])))},${json(stock)},${json({unit:'cm',note:'Bảng size minh họa; kiểm tra số đo trước khi đặt hàng.',sizes:['S','M','L','XL']})},${p.price},'ACTIVE',0,now(),now()) ON CONFLICT DO NOTHING;`);
    ['S','M','L','XL'].forEach(size => sql.push(`INSERT INTO product_variants(variant_id,product_id,sku,size,color,price_vnd,stock_quantity,status,version,created_at,updated_at) VALUES (${sqlString(id('variant:'+p.key+':'+size))},${sqlString(productId)},${sqlString('DEMO-FASHION-'+p.key.toUpperCase()+'-'+size)},${sqlString(size)},${sqlString(p.color)},${p.price},${stock[size]},'ACTIVE',0,now(),now()) ON CONFLICT DO NOTHING;`));
    p.images.forEach((asset,i) => sql.push(`INSERT INTO product_images(image_id,product_id,object_key,image_url,primary_image,sort_order,alt_text,created_at,updated_at) VALUES (${sqlString(id('image:'+p.key+':'+i))},${sqlString(productId)},${sqlString(asset.publicId)},${sqlString(asset.url)},${i===0},${i},${sqlString(p.name+' - ảnh '+(i+1))},now(),now()) ON CONFLICT DO NOTHING;`));
  }
  if (minimumStock !== null) {
    sql.push(`UPDATE product_variants v SET stock_quantity=greatest(v.stock_quantity,${minimumStock}),updated_at=now(),version=coalesce(v.version,0)+1 FROM products p WHERE p.product_id=v.product_id AND p.attributes->>'demoSeed'='fashion-v1' AND v.sku LIKE 'DEMO-FASHION-%' AND v.stock_quantity<${minimumStock};`);
    sql.push(`UPDATE products p SET sizes_stock=(SELECT jsonb_object_agg(s.size,s.stock) FROM (SELECT size,sum(stock_quantity) AS stock FROM product_variants WHERE product_id=p.product_id AND status='ACTIVE' GROUP BY size) s),updated_at=now() WHERE p.attributes->>'demoSeed'='fashion-v1';`);
  }
  sql.push('COMMIT;');
  const sqlFile = path.join(work, 'seed.sql');
  fs.writeFileSync(sqlFile, sql.join('\n'));
  pg('psql', ['-v','ON_ERROR_STOP=1','-f',sqlFile]);
  await clearCatalogCache();
  fs.writeFileSync(path.join(work,'summary.json'),JSON.stringify({shops:shops.map(([key,name])=>({id:id('shop:'+key),name})),products:products.map(p=>({id:id('product:'+p.key),name:p.name,source:p.sourceTitle,images:p.images.map(a=>a.url)})),backup},null,2));
  console.log(minimumStock === null ? 'Seed committed. Existing rows and edited stock were not overwritten.' : 'Seed committed. Only demo inventory below the requested minimum was increased.');
  console.log(pg('psql',['-At'],"SELECT 'fashion products: ' || count(*) FROM products WHERE attributes->>'demoSeed'='fashion-v1'; SELECT 'fashion variants: ' || count(*) FROM product_variants WHERE sku LIKE 'DEMO-FASHION-%';"));
}
main().catch(error => { console.error(error.message); process.exitCode=1; });
