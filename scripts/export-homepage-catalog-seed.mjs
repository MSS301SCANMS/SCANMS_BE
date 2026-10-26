import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const connectionFile=path.join(repo,'.local','product-cloud','app-connection.env');
const connection=fs.readFileSync(connectionFile,'utf8').split(/\r?\n/).find(l=>l.startsWith('DATABASE_URL='));
assert.ok(connection,'Private Product connection file is required for export');
const uri=new URL(connection.slice(13));
const env={...process.env,PGHOST:uri.hostname,PGPORT:uri.port||'5432',PGDATABASE:uri.pathname.slice(1),PGUSER:decodeURIComponent(uri.username),PGPASSWORD:decodeURIComponent(uri.password),PGSSLMODE:'require',PGCHANNELBINDING:'require',PGCONNECT_TIMEOUT:'20',PGCLIENTENCODING:'UTF8'};
const select={
  stores:'store_id,owner_user_id,name,description,logo_ref,approval_status',
  product_categories:'category_id,parent_id,name,slug,description,active,display_order',
  products:'product_id,store_id,category_id,name,description,category,material,attributes,image_refs,sizes_stock,size_guide,price_vnd,status',
  product_variants:'variant_id,product_id,sku,size,color,price_vnd,stock_quantity,status',
  product_images:'image_id,product_id,object_key,image_url,primary_image,sort_order,alt_text',
};
const query='SELECT json_build_object('+Object.entries(select).map(([table,columns])=>`'${table}',(SELECT json_agg(t ORDER BY t.${columns.split(',')[0]}) FROM (SELECT ${columns} FROM ${table}) t)`).join(',')+');';
const r=spawnSync(path.join(process.env.POSTGRES_BIN||'C:/Program Files/PostgreSQL/18/bin','psql.exe'),['-X','-A','-t','-v','ON_ERROR_STOP=1','-c',query],{env,encoding:'utf8',maxBuffer:16*1024*1024});
assert.equal(r.status,0,'Could not read Product catalog for export');
const data=JSON.parse(r.stdout.trim());
assert.equal(data.stores.length,1); assert.equal(data.products.length,24); assert.equal(data.product_variants.length,96); assert.equal(data.product_images.length,69);
const productIds=new Set(data.products.map(p=>p.product_id));
assert.equal(productIds.size,24);assert.ok(data.products.every(p=>p.store_id===data.stores[0].store_id&&p.status==='ACTIVE'));
data.product_categories??=[];
for(const product of data.products) {
  if(product.category_id&&!data.product_categories.some(c=>c.category_id===product.category_id)) {
    assert.ok(product.category,'Missing category name');
    const slug=product.category.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replaceAll('đ','d').replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
    data.product_categories.push({category_id:product.category_id,parent_id:null,name:product.category,slug:'fashion-demo-'+slug,
      description:'Danh mục catalog mặc định SCANMS',active:true,display_order:data.product_categories.length});
  }
}
assert.equal(data.product_categories.length,6);
assert.ok(data.product_variants.every(v=>productIds.has(v.product_id)&&Number.isSafeInteger(v.stock_quantity)&&v.stock_quantity>=0));
assert.ok(data.product_images.every(image=>productIds.has(image.product_id)&&image.image_url.startsWith('https://')));
const sqlValue=value=>value===null?'NULL':typeof value==='boolean'?(value?'TRUE':'FALSE'):typeof value==='number'?(assert.ok(Number.isSafeInteger(value)),String(value)):typeof value==='object'?"'"+JSON.stringify(value).replaceAll("'","''")+"'::jsonb":"'"+String(value).replaceAll("'","''")+"'";
const sql=[
  '-- SCANMS homepage catalog v1: 1 shop, 6 categories, 24 products, 96 variants, 69 images.',
  '-- Public catalog fields only. No owner contact details, accounts, reviews or orders.',
  '-- IDs and current variant stock are preserved; never overwrite existing rows.',
];
for(const [table,columns] of Object.entries(select)) {
  sql.push('');
  for(const row of data[table]) {
    const keys=columns.split(',');
    const extra=table==='stores'?['rating_average','rating_count','version']:['product_images','product_categories'].includes(table)?[]:['version'];
    const values=keys.map(k=>sqlValue(row[k]));
    sql.push(`INSERT INTO ${table} (${[...keys,...extra,'created_at','updated_at'].join(',')}) VALUES (${[...values,...extra.map(()=>'0'),'CURRENT_TIMESTAMP','CURRENT_TIMESTAMP'].join(',')}) ON CONFLICT DO NOTHING;`);
  }
}
const resource=path.join(repo,'product-service','src','main','resources','seed','homepage-catalog.sql');
fs.mkdirSync(path.dirname(resource),{recursive:true}); fs.writeFileSync(resource,sql.join('\n')+'\n');
console.log(JSON.stringify({resource,stores:1,products:24,variants:96,images:69,privateOwnerDetailsIncluded:false,credentialsIncluded:false}));
