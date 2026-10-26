import urllib.request
import urllib.parse
import json
import uuid
import subprocess
import time
import socket

print("=== BẮT ĐẦU SEED DỮ LIỆU THỜI TRANG CHO SCANMS ===")

# -------------------------------------------------------------
# 1. KEYCLOAK & USER SERVICE (user_db)
# -------------------------------------------------------------
print("\n[1/6] Đang kết nối Keycloak Admin...")
req = urllib.request.Request(
    'http://localhost:8180/realms/master/protocol/openid-connect/token',
    data=b'client_id=admin-cli&grant_type=password&username=admin&password=admin'
)
try:
    token = json.loads(urllib.request.urlopen(req, timeout=10).read())['access_token']
    print("  -> Kết nối Keycloak thành công!")
except Exception as e:
    print("  -> Lỗi kết nối Keycloak:", e)
    token = None

# Ensure Keycloak roles
roles = ['USER', 'KOL_CTV', 'SHOP_OWNER', 'MANAGER', 'ADMIN']
if token:
    for rname in roles:
        req_r = urllib.request.Request(
            'http://localhost:8180/admin/realms/scanms/roles',
            data=json.dumps({'name': rname}).encode('utf-8'),
            headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
            method='POST'
        )
        try:
            urllib.request.urlopen(req_r, timeout=5)
        except Exception:
            pass

    # Ensure scanms-client service account has realm-management roles
    try:
        req_clients = urllib.request.Request('http://localhost:8180/admin/realms/scanms/clients?clientId=scanms-client', headers={'Authorization': 'Bearer ' + token})
        clients = json.loads(urllib.request.urlopen(req_clients).read())
        if clients:
            c_id = clients[0]['id']
            req_sa = urllib.request.Request(f'http://localhost:8180/admin/realms/scanms/clients/{c_id}/service-account-user', headers={'Authorization': 'Bearer ' + token})
            sa_id = json.loads(urllib.request.urlopen(req_sa).read())['id']

            req_rm = urllib.request.Request('http://localhost:8180/admin/realms/scanms/clients?clientId=realm-management', headers={'Authorization': 'Bearer ' + token})
            rm_id = json.loads(urllib.request.urlopen(req_rm).read())[0]['id']

            req_rm_roles = urllib.request.Request(f'http://localhost:8180/admin/realms/scanms/clients/{rm_id}/roles', headers={'Authorization': 'Bearer ' + token})
            all_rm_roles = json.loads(urllib.request.urlopen(req_rm_roles).read())
            target_rm = [r for r in all_rm_roles if r['name'] in ['realm-admin', 'manage-users', 'view-users', 'query-users', 'impersonation']]

            req_assign_rm = urllib.request.Request(
                f'http://localhost:8180/admin/realms/scanms/users/{sa_id}/role-mappings/clients/{rm_id}',
                data=json.dumps(target_rm).encode('utf-8'),
                headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
                method='POST'
            )
            urllib.request.urlopen(req_assign_rm)
            print('  -> Đã cấp quyền realm-admin cho scanms-client service account!')
    except Exception as e:
        print('  -> Lưu ý cấp quyền scanms-client:', e)

# Accounts definition
test_users = [
    {
        'username': 'shopowner01',
        'email': 'shopowner01@scanms.com',
        'fullName': 'Nguyễn Văn Đức (Mộc Basics)',
        'firstName': 'Nguyễn Văn',
        'lastName': 'Đức',
        'roles': ['USER', 'SHOP_OWNER'],
        'phone': '0922333444',
        'avatar': 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=400&auto=format&fit=crop&q=80',
        'bio': 'Chủ sáng lập thương hiệu thời trang bền vững Mộc Basics. Đam mê chất liệu Linen & Cotton mộc.',
        'userId': '6e405eec-561b-4e57-bed2-098d38dc3abf'
    },
    {
        'username': 'kolctv01',
        'email': 'kolctv01@scanms.com',
        'fullName': 'Lê Thùy Linh',
        'firstName': 'Lê Thùy',
        'lastName': 'Linh',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0911222333',
        'avatar': 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80',
        'bio': 'Mix & Match Stylist | Hướng dẫn phối đồ công sở & thường ngày cho nàng hiện đại',
        'userId': '0773dbd2-ef49-4aee-9089-231946d28279'
    },
    {
        'username': 'quynhanhshyn',
        'email': 'quynhanhshyn@scanms.com',
        'fullName': 'Quỳnh Anh Shyn',
        'firstName': 'Quỳnh Anh',
        'lastName': 'Shyn',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0988112233',
        'avatar': 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop&q=80',
        'bio': 'Top Fashionista Vietnam | Trendsetter | Đại diện tham dự các tuần lễ thời trang quốc tế Milan, Paris',
        'userId': '33333333-1111-4000-8000-000000000001'
    },
    {
        'username': 'thaonhile',
        'email': 'thaonhile@scanms.com',
        'fullName': 'Thảo Nhi Lê',
        'firstName': 'Thảo Nhi',
        'lastName': 'Lê',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0977223344',
        'avatar': 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=400&auto=format&fit=crop&q=80',
        'bio': 'Fashion Designer & Influencer | Founder Daphale Studios | Á hậu Hoàn vũ VN',
        'userId': '33333333-1111-4000-8000-000000000002'
    },
    {
        'username': 'chaubui',
        'email': 'chaubui@scanms.com',
        'fullName': 'Châu Bùi',
        'firstName': 'Châu',
        'lastName': 'Bùi',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0966334455',
        'avatar': 'https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?w=400&auto=format&fit=crop&q=80',
        'bio': 'Fashion Icon & Stylist | Forbes 30 Under 30 Asia | Đam mê thời trang bền vững và phong cách phá cách',
        'userId': '33333333-1111-4000-8000-000000000003'
    },
    {
        'username': 'phiphuonganh',
        'email': 'phiphuonganh@scanms.com',
        'fullName': 'Phí Phương Anh',
        'firstName': 'Phí Phương',
        'lastName': 'Anh',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0955445566',
        'avatar': 'https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=400&auto=format&fit=crop&q=80',
        'bio': 'The Face Champion | Trendy Outfits & Gen Z Fashion Review',
        'userId': '33333333-1111-4000-8000-000000000004'
    },
    {
        'username': 'fabonguyen',
        'email': 'fabonguyen@scanms.com',
        'fullName': 'Fabo Nguyễn',
        'firstName': 'Fabo',
        'lastName': 'Nguyễn',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0944556677',
        'avatar': 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop&q=80',
        'bio': 'Streetwear Enthusiast & Sneaker Collector | Đánh giá phong cách Hypebeast & Denim bụi bặm',
        'userId': '33333333-1111-4000-8000-000000000005'
    },
    {
        'username': 'customer01',
        'email': 'customer01@scanms.com',
        'fullName': 'Trần Hoàng Mai Anh',
        'firstName': 'Hoàng Mai',
        'lastName': 'Anh',
        'roles': ['USER'],
        'phone': '0933889900',
        'avatar': 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&auto=format&fit=crop&q=80',
        'bio': 'Tín đồ mua sắm thời trang online & Reviewer trải nghiệm sản phẩm chân thực.',
        'userId': '44444444-1111-4000-8000-000000000001'
    },
    {
        'username': 'manager01',
        'email': 'manager01@scanms.com',
        'fullName': 'Nguyễn Hoàng Quản Lý',
        'firstName': 'Hoàng',
        'lastName': 'Quản Lý',
        'roles': ['USER', 'MANAGER'],
        'phone': '0933444555',
        'avatar': 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop&q=80',
        'bio': 'Quản lý chất lượng sàn thời trang & duyệt kiểm duyệt đối tác SCANMS.',
        'userId': 'a10cecf8-a4d6-42f3-937a-cead57887258'
    },
    {
        'username': 'admin',
        'email': 'admin@scanms.com',
        'fullName': 'System Administrator',
        'firstName': 'System',
        'lastName': 'Administrator',
        'roles': ['USER', 'ADMIN'],
        'phone': '0944555666',
        'avatar': 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400&auto=format&fit=crop&q=80',
        'bio': 'Quản trị viên toàn hệ thống sàn SCANMS.',
        'userId': '8fb750dc-7df9-4f89-9109-86cf2afb1eb6'
    }
]

role_map = {
    'ADMIN': '4b3d9dd3-a16e-4956-b72e-c99e828c57e4',
    'MANAGER': 'c06aaaea-1dc1-4fdb-bdd0-7d34ac4840ad',
    'USER': '3286c240-67cf-4068-878e-3285643a48c3',
    'KOL_CTV': '32368142-1147-41d9-b5f2-639c9ae52025',
    'SHOP_OWNER': '4ea22f2e-c1fa-4c93-bcd4-8897d1a89927'
}

user_sql = []
for u in test_users:
    kc_uid = None
    if token:
        # Check if user exists
        req_s = urllib.request.Request(
            f"http://localhost:8180/admin/realms/scanms/users?username={u['username']}",
            headers={'Authorization': 'Bearer ' + token}
        )
        try:
            res_s = json.loads(urllib.request.urlopen(req_s, timeout=5).read())
            if res_s:
                kc_uid = res_s[0]['id']
            else:
                user_payload = {
                    'username': u['username'],
                    'email': u['email'],
                    'enabled': True,
                    'emailVerified': True,
                    'firstName': u['firstName'],
                    'lastName': u['lastName'],
                    'credentials': [{'type': 'password', 'value': '123456', 'temporary': False}]
                }
                req_u = urllib.request.Request(
                    'http://localhost:8180/admin/realms/scanms/users',
                    data=json.dumps(user_payload).encode('utf-8'),
                    headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
                    method='POST'
                )
                urllib.request.urlopen(req_u, timeout=5)
                # re-fetch
                res_s2 = json.loads(urllib.request.urlopen(req_s, timeout=5).read())
                if res_s2:
                    kc_uid = res_s2[0]['id']
        except Exception as e:
            print(f"  -> Lưu ý Keycloak user {u['username']}: {e}")

        if kc_uid:
            roles_to_assign = []
            for rname in u['roles']:
                try:
                    req_role_info = urllib.request.Request(
                        f'http://localhost:8180/admin/realms/scanms/roles/{rname}',
                        headers={'Authorization': 'Bearer ' + token}
                    )
                    r_info = json.loads(urllib.request.urlopen(req_role_info, timeout=5).read())
                    roles_to_assign.append({'id': r_info['id'], 'name': r_info['name']})
                except Exception:
                    pass
            if roles_to_assign:
                req_assign = urllib.request.Request(
                    f'http://localhost:8180/admin/realms/scanms/users/{kc_uid}/role-mappings/realm',
                    data=json.dumps(roles_to_assign).encode('utf-8'),
                    headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
                    method='POST'
                )
                try:
                    urllib.request.urlopen(req_assign, timeout=5)
                except Exception:
                    pass

    if not kc_uid:
        kc_uid = str(uuid.uuid5(uuid.NAMESPACE_DNS, u['email']))

    u['kc_uid'] = kc_uid
    user_sql.append(f"""
INSERT INTO users (user_id, created_at, updated_at, email, full_name, identity_subject, status, phone_number, username, bio, avatar_url)
VALUES ('{u['userId']}', NOW(), NOW(), '{u['email']}', '{u['fullName']}', '{kc_uid}', 'ACTIVE', '{u['phone']}', '{u['username']}', '{u['bio']}', '{u['avatar']}')
ON CONFLICT (email) DO UPDATE SET
  full_name = '{u['fullName']}',
  identity_subject = '{kc_uid}',
  status = 'ACTIVE',
  phone_number = '{u['phone']}',
  bio = '{u['bio']}',
  avatar_url = '{u['avatar']}';
""")
    for rname in u['roles']:
        rid = role_map[rname]
        user_sql.append(f"""
INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '{rid}' FROM users WHERE email = '{u['email']}'
ON CONFLICT DO NOTHING;
""")

print("  -> Đang thực thi seed vào scanms-user-db...")
full_user_sql = "\n".join(user_sql)
subprocess.run(['docker', 'exec', '-i', 'scanms-user-db', 'psql', '-U', 'postgres', '-d', 'user_db'], input=full_user_sql.encode('utf-8'), check=True)
print("  -> Seed user_db hoàn tất!")

# -------------------------------------------------------------
# 2. PRODUCT SERVICE (product_db) - THỜI TRANG & QUẦN ÁO
# -------------------------------------------------------------
print("\n[2/6] Đang seed danh mục, gian hàng & sản phẩm Thời Trang vào product_db...")

product_db_sql = []
product_db_sql.append("BEGIN;")

# Ensure text description column
product_db_sql.append("ALTER TABLE products ALTER COLUMN description TYPE text;")

# Categories (Thời trang)
categories = [
    ('cat-ao-thun', 'ao-thun', 'Áo Thun & Polo', 'Áo thun basic, graphic tee, polo thoáng mát'),
    ('cat-ao-so-mi', 'ao-so-mi', 'Áo Sơ Mi', 'Áo sơ mi linen, sơ mi công sở, sơ mi cổ bẻ phong cách thanh lịch'),
    ('cat-ao-khoac', 'ao-khoac-blazer', 'Áo Khoác & Blazer', 'Blazer form rộng, áo khoác dạ, jacket, cardigan và bomber'),
    ('cat-dam-vay', 'dam-vay', 'Đầm & Chân Váy', 'Đầm midi, đầm xòe, đầm lụa dự tiệc và chân váy xếp ly'),
    ('cat-quan-jeans', 'quan-jeans', 'Quần & Jeans', 'Quần ống rộng, quần tây ống suông, jeans ống đứng và quần short'),
    ('cat-set-trang-phuc', 'set-trang-phuc', 'Set Trang Phục', 'Bộ phối đồ ready-to-wear, set đồ co-ords thời thượng')
]

for cid, slug, cname, cdesc in categories:
    product_db_sql.append(f"""
INSERT INTO product_categories (category_id, name, slug, description, active, display_order, created_at, updated_at)
VALUES ('{cid}', '{cname}', '{slug}', '{cdesc}', true, 1, NOW(), NOW())
ON CONFLICT (slug) DO UPDATE SET name = '{cname}', description = '{cdesc}';
""")

# 5 Stores (Gian hàng thời trang)
stores = [
    {
        'id': '11111111-0000-0000-0000-000000000001',
        'owner_id': '6e405eec-561b-4e57-bed2-098d38dc3abf', # shopowner01
        'name': 'Mộc Basics - Thời Trang Bền Vững',
        'desc': 'Thương hiệu thời trang may mặc bền vững, phong cách tối giản từ chất liệu Linen & Cotton 100% tự nhiên.',
        'logo': 'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=400&auto=format&fit=crop&q=80',
        'rating': 4.90,
        'rating_count': 142
    },
    {
        'id': '11111111-0000-0000-0000-000000000002',
        'owner_id': '33333333-1111-4000-8000-000000000001', # quynhanhshyn
        'name': 'Nếp Studio - Đầm Thiết Kế & Công Sở',
        'desc': 'Thời trang nữ cao cấp, form dáng chuẩn mực, tôn vinh nét đẹp thanh lịch của người phụ nữ Việt.',
        'logo': 'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=400&auto=format&fit=crop&q=80',
        'rating': 4.86,
        'rating_count': 98
    },
    {
        'id': '11111111-0000-0000-0000-000000000003',
        'owner_id': '33333333-1111-4000-8000-000000000005', # fabonguyen
        'name': 'Phố Streetwear - Cá Tính Gen Z',
        'desc': 'Phong cách đường phố cá tính, áo thun oversize, hoodie nỉ bông, quần túi hộp thời thượng.',
        'logo': 'https://images.unsplash.com/photo-1509631179647-0177331693ae?w=400&auto=format&fit=crop&q=80',
        'rating': 4.94,
        'rating_count': 215
    },
    {
        'id': '11111111-0000-0000-0000-000000000004',
        'owner_id': '33333333-1111-4000-8000-000000000003', # chaubui
        'name': 'Lụa Atelier - Nét Đẹp Đương Đại',
        'desc': 'Áo dài cách tân, váy lụa tơ tằm thượng hạng giao thoa giữa nét đẹp truyền thống và hơi thở đương đại.',
        'logo': 'https://images.unsplash.com/photo-1558769132-cb1aea458c5e?w=400&auto=format&fit=crop&q=80',
        'rating': 4.96,
        'rating_count': 76
    },
    {
        'id': '11111111-0000-0000-0000-000000000005',
        'owner_id': '33333333-1111-4000-8000-000000000002', # thaonhile
        'name': 'Daphale Studio - Quyến Rũ & Tự Do',
        'desc': 'Thời trang cá tính, corset định hình, đầm bodycon và blazer vai độn mang phong cách phóng khoáng, tự tin.',
        'logo': 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=400&auto=format&fit=crop&q=80',
        'rating': 4.88,
        'rating_count': 160
    }
]

for s in stores:
    owner_details = json.dumps({"address": "72 Lê Thánh Tôn, Bến Nghé, Quận 1, TP. Hồ Chí Minh", "hotline": "1900 6868"}).replace("'", "''")
    product_db_sql.append(f"""
INSERT INTO stores (store_id, owner_user_id, name, description, logo_ref, approval_status, rating_average, rating_count, owner_details, version, created_at, updated_at)
VALUES ('{s['id']}', '{s['owner_id']}', '{s['name']}', '{s['desc']}', '{s['logo']}', 'APPROVED', {s['rating']}, {s['rating_count']}, '{owner_details}'::jsonb, 0, NOW(), NOW())
ON CONFLICT (store_id) DO UPDATE SET
  owner_user_id = '{s['owner_id']}',
  name = '{s['name']}',
  description = '{s['desc']}',
  logo_ref = '{s['logo']}',
  approval_status = 'APPROVED',
  rating_average = {s['rating']},
  rating_count = {s['rating_count']};
""")

# 18 Clothing Products
products_data = [
    # Mộc Basics (Store 1)
    {
        'id': 'prod-moc-01',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'cat_id': 'cat-ao-so-mi',
        'cat_name': 'Áo Sơ Mi',
        'name': 'Áo Sơ Mi Linen Tay Dài Form Suông Mộc Basics',
        'desc': 'Áo sơ mi chất liệu 100% Linen Pháp tự nhiên đã qua xử lý wash mềm. Form suông rộng thoáng mát, thấm hút mồ hôi vượt trội, phù hợp cho cả đi làm lẫn dạo phố cuối tuần.',
        'price': 389000,
        'material': '100% Linen Pháp tự nhiên',
        'color': 'Be Mộc',
        'images': [
            'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-moc-02',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'cat_id': 'cat-ao-thun',
        'cat_name': 'Áo Thun & Polo',
        'name': 'Áo Thun Cotton Compact 250gsm Thoáng Khí',
        'desc': 'Áo thun trơn cổ tròn basic với công nghệ dệt sợi Compact 100% cotton chống xù lông. Độ dày 250gsm đứng form, co giãn 4 chiều mềm mại, bền màu sau hơn 100 lần giặt.',
        'price': 249000,
        'material': 'Cotton Compact 250gsm',
        'color': 'Trắng Tinh Khôi',
        'images': [
            'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-moc-03',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'cat_id': 'cat-quan-jeans',
        'cat_name': 'Quần & Jeans',
        'name': 'Quần Dài Linen Ống Suông Lưng Thun Tiện Lợi',
        'desc': 'Quần đũi linen ống rộng thoải mái với cạp thun co giãn kết hợp dây rút tiện lợi. Tôn dáng, che khuyết điểm chân, mang lại cảm giác nhẹ nhàng, dễ chịu trong mọi hoạt động.',
        'price': 429000,
        'material': 'Đũi Linen dệt tự nhiên',
        'color': 'Nâu Khói',
        'images': [
            'https://images.unsplash.com/photo-1506629082955-511b1aa562c8?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1517445312882-bc9910d016b7?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-moc-04',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'cat_id': 'cat-ao-khoac',
        'cat_name': 'Áo Khoác & Blazer',
        'name': 'Áo Khoác Blazer Đũi Mộc Dáng Suông Hàn Quốc',
        'desc': 'Blazer 1 lớp từ vải đũi cao cấp nhẹ tênh, may viền bọc cẩn thận. Thiết kế phong cách tối giản thanh lịch, tạo điểm nhấn ấn tượng cho bộ trang phục thường ngày.',
        'price': 680000,
        'material': 'Vải đũi cao cấp dệt sợi mộc',
        'color': 'Kem Cát',
        'images': [
            'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1548624149-f9b1859aa7d0?w=800&auto=format&fit=crop&q=80'
        ]
    },
    # Nếp Studio (Store 2)
    {
        'id': 'prod-nep-01',
        'store_id': '11111111-0000-0000-0000-000000000002',
        'cat_id': 'cat-dam-vay',
        'cat_name': 'Đầm & Chân Váy',
        'name': 'Đầm Midi Hoa Nhí Cổ V Tay Phồng Nếp Studio',
        'desc': 'Chiếc đầm hoa nhí dịu dàng làm từ voan tơ cao cấp lót lụa habutai mềm mượt. Thiết kế chiết eo nhẹ nhàng khéo léo tôn vòng 2 thon thả, thích hợp cho nàng dự tiệc nhẹ hoặc hẹn hò.',
        'price': 690000,
        'material': 'Voan tơ lụa habutai',
        'color': 'Hồng Phấn Hoa',
        'images': [
            'https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1496747611176-843222e1e57c?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-nep-02',
        'store_id': '11111111-0000-0000-0000-000000000002',
        'cat_id': 'cat-set-trang-phuc',
        'cat_name': 'Set Trang Phục',
        'name': 'Set Áo Vest Cộc Tay Phối Chân Váy Xếp Ly Công Sở',
        'desc': 'Bộ suit 2 món phối màu pastel tinh tế gồm áo gile cộc tay đính cúc ngọc trai và chân váy xếp ly chuẩn phom. Mang đến vẻ ngoài sang trọng, chỉn chu cho quý cô văn phòng hiện đại.',
        'price': 850000,
        'material': 'Kaki lụa dệt chéo cao cấp',
        'color': 'Xám Tro Thanh Lịch',
        'images': [
            'https://images.unsplash.com/photo-1583496661160-fb5886a0aaaa?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-nep-03',
        'store_id': '11111111-0000-0000-0000-000000000002',
        'cat_id': 'cat-dam-vay',
        'cat_name': 'Đầm & Chân Váy',
        'name': 'Chân Váy Chữ A Dập Ly Nổi Cao Cấp',
        'desc': 'Chân váy dáng chữ A dài qua gối với kỹ thuật dập ly nhiệt bền bỉ không bai giãn sau khi giặt. Dễ dàng mix cùng áo sơ mi, áo len mỏng hay áo phông basic.',
        'price': 380000,
        'material': 'Tuyết mưa dập ly nhiệt',
        'color': 'Đen Quyền Lực',
        'images': [
            'https://images.unsplash.com/photo-1582142306909-195724d33ffc?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1572804013309-59a88b7e92f1?w=800&auto=format&fit=crop&q=80'
        ]
    },
    # Phố Streetwear (Store 3)
    {
        'id': 'prod-pho-01',
        'store_id': '11111111-0000-0000-0000-000000000003',
        'cat_id': 'cat-ao-thun',
        'cat_name': 'Áo Thun & Polo',
        'name': 'Áo Thun Graphic Oversize Vintage Acid Wash',
        'desc': 'Áo thun streetwear form rộng oversize với hiệu ứng giặt acid wash cổ điển. Họa tiết typography dập nổi thủ công đậm chất đường phố, khẳng định phong cách cá nhân Gen Z.',
        'price': 290000,
        'material': 'Cotton 100% 2 chiều 260gsm',
        'color': 'Xám Khói Acid Wash',
        'images': [
            'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1529374255404-311a2a4f1fd9?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-pho-02',
        'store_id': '11111111-0000-0000-0000-000000000003',
        'cat_id': 'cat-ao-khoac',
        'cat_name': 'Áo Khoác & Blazer',
        'name': 'Áo Hoodie Nỉ Bông Grunge Phố Cổ Điển',
        'desc': 'Áo nỉ chui đầu có mũ rộng với chất liệu nỉ bông chân cua 380gsm giữ ấm cực tốt. Bo tay và gấu áo dệt rib co giãn tốt, túi kangaroo rộng rãi tiện dụng.',
        'price': 520000,
        'material': 'Nỉ bông chân cua 380gsm',
        'color': 'Đen Than',
        'images': [
            'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1509967419530-da38b4704bc6?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-pho-03',
        'store_id': '11111111-0000-0000-0000-000000000003',
        'cat_id': 'cat-quan-jeans',
        'cat_name': 'Quần & Jeans',
        'name': 'Quần Túi Hộp Cargo Pants Ống Rộng Unisex',
        'desc': 'Quần túi hộp phong cách nhảy và dạo phố với 6 túi tiện lợi. Chất liệu vải kaki chống nhăn đứng dáng, ống quần có khóa rút linh hoạt chuyển đổi giữa ống suông và jogger.',
        'price': 460000,
        'material': 'Kaki dù nhập khẩu',
        'color': 'Rêu Quân Đội',
        'images': [
            'https://images.unsplash.com/photo-1517445312882-bc9910d016b7?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1506629082955-511b1aa562c8?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-pho-04',
        'store_id': '11111111-0000-0000-0000-000000000003',
        'cat_id': 'cat-ao-khoac',
        'cat_name': 'Áo Khoác & Blazer',
        'name': 'Áo Khoác Bomber Varsity Phối Da Thêu Logo',
        'desc': 'Varsity jacket huyền thoại thân vải dạ ép ấm áp kết hợp tay da PU cao cấp. Thêu nổi 3D trước ngực và sau lưng, cúc bấm kim loại đúc chắc chắn.',
        'price': 720000,
        'material': 'Dạ ép thân + Tay da PU',
        'color': 'Xanh Rêu Phối Kem',
        'images': [
            'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1548883354-7622d03aca27?w=800&auto=format&fit=crop&q=80'
        ]
    },
    # Lụa Atelier (Store 4)
    {
        'id': 'prod-lua-01',
        'store_id': '11111111-0000-0000-0000-000000000004',
        'cat_id': 'cat-dam-vay',
        'cat_name': 'Đầm & Chân Váy',
        'name': 'Áo Dài Cách Tân Tơ Tằm Thêu Sen Thủ Công',
        'desc': 'Tác phẩm áo dài 4 tà dệt từ tơ tằm Bảo Lộc thượng hạng. Họa tiết hoa sen được nghệ nhân thêu tay tỉ mỉ từng đường kim mũi chỉ, mang lại vẻ đẹp thanh tao, quý phái thuần Việt.',
        'price': 1190000,
        'material': 'Tơ tằm tự nhiên Bảo Lộc 100%',
        'color': 'Hồng Tơ Sen',
        'images': [
            'https://images.unsplash.com/photo-1618932260643-eee4a2f652a6?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1566174053879-31528523f8ae?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-lua-02',
        'store_id': '11111111-0000-0000-0000-000000000004',
        'cat_id': 'cat-dam-vay',
        'cat_name': 'Đầm & Chân Váy',
        'name': 'Váy Lụa Hai Dây Maxi Xẻ Tà Sang Trọng Lụa Atelier',
        'desc': 'Váy lụa hai dây suôn mềm óng ả ôm trọn đường cong cơ thể. Thiết kế xẻ tà cao tinh tế giúp từng bước di chuyển thêm phần thướt tha, là lựa chọn số một cho các buổi dạ tiệc.',
        'price': 890000,
        'material': 'Lụa Satin dệt tơ bóng tự nhiên',
        'color': 'Đỏ Rượu Vang',
        'images': [
            'https://images.unsplash.com/photo-1566174053879-31528523f8ae?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1539008835657-9e8e9680c956?w=800&auto=format&fit=crop&q=80'
        ]
    },
    # Daphale Studio (Store 5)
    {
        'id': 'prod-daph-01',
        'store_id': '11111111-0000-0000-0000-000000000005',
        'cat_id': 'cat-ao-thun',
        'cat_name': 'Áo Thun & Polo',
        'name': 'Áo Corset Cúp Ngực Định Hình Gọng Thép Daphale',
        'desc': 'Áo corset thiết kế độc quyền tôn dáng với hệ thống gọng thép dẻo định hình vòng eo con kiến nhưng vẫn êm ái, dễ thở. Dây đan phía sau tùy chỉnh độ ôm hoàn hảo.',
        'price': 680000,
        'material': 'Vải dệt kim cao cấp kèm gọng định hình',
        'color': 'Đen Huyền Bí',
        'images': [
            'https://images.unsplash.com/photo-1509631179647-0177331693ae?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-daph-02',
        'store_id': '11111111-0000-0000-0000-000000000005',
        'cat_id': 'cat-dam-vay',
        'cat_name': 'Đầm & Chân Váy',
        'name': 'Đầm Bodycon Dệt Kim Ôm Sát Cắt Xẻ Táo Bạo',
        'desc': 'Đầm ôm sát body chất liệu len dệt kim co giãn 4 chiều dày dặn không lộ viền. Chi tiết cut-out eo tinh tế tạo điểm nhấn hiện đại, cuốn hút mọi ánh nhìn.',
        'price': 750000,
        'material': 'Thun dệt kim Rib co giãn 4 chiều',
        'color': 'Nâu Mocha',
        'images': [
            'https://images.unsplash.com/photo-1548624149-f9b1859aa7d0?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=800&auto=format&fit=crop&q=80'
        ]
    },
    {
        'id': 'prod-daph-03',
        'store_id': '11111111-0000-0000-0000-000000000005',
        'cat_id': 'cat-ao-khoac',
        'cat_name': 'Áo Khoác & Blazer',
        'name': 'Áo Blazer Oversize Vai Độn Quyền Lực High-End',
        'desc': 'Chiếc blazer vai độn mang cảm hứng thời trang Parisian Chic. Lớp lót lụa tơ cao cấp, đường may ve áo sắc nét, toát lên thần thái tự tin, bản lĩnh của quý cô thời thượng.',
        'price': 950000,
        'material': 'Vải tuyết mưa Hàn Quốc 2 lớp cao cấp',
        'color': 'Đen Tuyển',
        'images': [
            'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&auto=format&fit=crop&q=80',
            'https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?w=800&auto=format&fit=crop&q=80'
        ]
    }
]

sizes = ['S', 'M', 'L', 'XL']

for p in products_data:
    sizes_stock_json = json.dumps({'S': 80, 'M': 100, 'L': 90, 'XL': 50}).replace("'", "''")
    image_refs_dict = {'main': p['images'][0]}
    for idx, img in enumerate(p['images'][1:], 1):
        image_refs_dict[f'gallery{idx}'] = img
    image_refs_json = json.dumps(image_refs_dict).replace("'", "''")

    attributes_json = json.dumps({
        'color': p['color'],
        'material': p['material'],
        'season': 'Tất cả các mùa',
        'origin': 'Việt Nam'
    }, ensure_ascii=False).replace("'", "''")

    size_guide_json = json.dumps({
        'unit': 'cm',
        'sizes': {
            'S': '45 - 52 kg, Chiều cao 1m50 - 1m60',
            'M': '53 - 60 kg, Chiều cao 1m60 - 1m68',
            'L': '61 - 68 kg, Chiều cao 1m68 - 1m75',
            'XL': '69 - 78 kg, Chiều cao 1m72 - 1m82'
        }
    }, ensure_ascii=False).replace("'", "''")

    product_db_sql.append(f"""
INSERT INTO products (product_id, store_id, category_id, name, description, category, material, attributes, image_refs, sizes_stock, size_guide, price_vnd, status, version, created_at, updated_at)
VALUES ('{p['id']}', '{p['store_id']}', '{p['cat_id']}', '{p['name']}', '{p['desc']}', '{p['cat_name']}', '{p['material']}', '{attributes_json}'::jsonb, '{image_refs_json}'::jsonb, '{sizes_stock_json}'::jsonb, '{size_guide_json}'::jsonb, {p['price']}, 'ACTIVE', 0, NOW(), NOW())
ON CONFLICT (product_id) DO UPDATE SET
  name = '{p['name']}',
  description = '{p['desc']}',
  category = '{p['cat_name']}',
  price_vnd = {p['price']},
  material = '{p['material']}',
  image_refs = '{image_refs_json}'::jsonb,
  sizes_stock = '{sizes_stock_json}'::jsonb,
  status = 'ACTIVE';
""")

    # Variants (S, M, L, XL)
    for sz in sizes:
        var_id = f"var-{p['id']}-{sz.lower()}"
        sku = f"SCANMS-FASHION-{p['id'].upper()}-{sz}"
        stock = 100 if sz in ['M', 'L'] else 80
        product_db_sql.append(f"""
INSERT INTO product_variants (variant_id, product_id, sku, size, color, price_vnd, stock_quantity, status, version, created_at, updated_at)
VALUES ('{var_id}', '{p['id']}', '{sku}', '{sz}', '{p['color']}', {p['price']}, {stock}, 'ACTIVE', 0, NOW(), NOW())
ON CONFLICT (variant_id) DO UPDATE SET
  price_vnd = {p['price']},
  stock_quantity = {stock},
  status = 'ACTIVE';
""")

    # Product Images
    for idx, img_url in enumerate(p['images']):
        img_id = f"img-{p['id']}-{idx}"
        is_primary = (idx == 0)
        product_db_sql.append(f"""
INSERT INTO product_images (image_id, product_id, object_key, image_url, primary_image, sort_order, alt_text, created_at, updated_at)
VALUES ('{img_id}', '{p['id']}', '{p['id']}/img{idx}', '{img_url}', {str(is_primary).lower()}, {idx}, '{p['name']}', NOW(), NOW())
ON CONFLICT (image_id) DO UPDATE SET image_url = '{img_url}';
""")

# Feedbacks (Đánh giá sản phẩm thời trang chân thực)
feedbacks_sample = [
    ('fb-01', 'prod-moc-01', '44444444-1111-4000-8000-000000000001', 'item-seed-01', 5, 'Chất vải Linen dày dặn, wash mềm sờ rất thích tay. Mình cao 1m65 nặng 54kg mặc size M vừa in form rất đẹp.'),
    ('fb-02', 'prod-moc-02', '44444444-1111-4000-8000-000000000001', 'item-seed-02', 5, 'Áo thun mặc mát mẻ, cổ áo ôm đẹp không bị dão khi giặt máy. Đóng gói hộp rất chỉn chu, 10 điểm cho Mộc Basics!'),
    ('fb-03', 'prod-nep-01', '44444444-1111-4000-8000-000000000001', 'item-seed-03', 5, 'Váy hoa nhí mặc lên tôn da cực kỳ, lớp lót lụa habutai êm mịn đi chơi ai cũng khen.'),
    ('fb-04', 'prod-pho-01', '44444444-1111-4000-8000-000000000001', 'item-seed-04', 5, 'Form áo oversize chuẩn đường phố, vải cotton 100% cầm nặng tay, hình in vintage sắc nét.'),
    ('fb-05', 'prod-lua-01', '44444444-1111-4000-8000-000000000001', 'item-seed-05', 5, 'Áo dài lụa tơ tằm xuất sắc ngoài mong đợi! Đường thêu hoa sen tỉ mỉ, chất tơ mềm rủ sang trọng.')
]

for fid, pid, cid, oiid, rating, comment in feedbacks_sample:
    product_db_sql.append(f"""
INSERT INTO feedbacks (feedback_id, product_id, customer_id, order_item_id, rating, comment, status, created_at, updated_at)
VALUES ('{fid}', '{pid}', '{cid}', '{oiid}', {rating}, '{comment}', 'ACTIVE', NOW(), NOW())
ON CONFLICT (feedback_id) DO UPDATE SET comment = '{comment}', rating = {rating};
""")

# Livestreams (Phiên live thời trang)
livestreams = [
    {
        'id': 'live-summer-01',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'title': 'Đại Tiệc Thời Trang Mộc Basics x Châu Bùi: Ra Mắt BST Mùa Hè',
        'desc': 'Trực tiếp thử đồ và nhận ngàn voucher giảm 30% cùng Fashionista Châu Bùi.',
        'status': 'LIVE',
        'viewer_count': 1420,
        'unique_viewers': 3850,
        'product_refs': json.dumps({'productIds': ['prod-moc-01', 'prod-moc-02', 'prod-moc-03', 'prod-moc-04']}),
        'collab_refs': json.dumps({'collaboratorIds': ['33333333-1111-4000-8000-000000000003']})
    },
    {
        'id': 'live-weekend-02',
        'store_id': '11111111-0000-0000-0000-000000000002',
        'title': 'Nếp Studio x Quỳnh Anh Shyn: Tỏa Sáng Công Sở',
        'desc': 'Bí kíp mix match đầm thiết kế và vest cộc tay thanh lịch cho phái đẹp.',
        'status': 'SCHEDULED',
        'viewer_count': 0,
        'unique_viewers': 0,
        'product_refs': json.dumps({'productIds': ['prod-nep-01', 'prod-nep-02', 'prod-nep-03']}),
        'collab_refs': json.dumps({'collaboratorIds': ['33333333-1111-4000-8000-000000000001']})
    }
]

for lv in livestreams:
    product_db_sql.append(f"""
INSERT INTO livestreams (livestream_id, store_id, title, description, status, viewer_count, unique_viewer_count, product_refs, collaborator_refs, created_at, updated_at)
VALUES ('{lv['id']}', '{lv['store_id']}', '{lv['title']}', '{lv['desc']}', '{lv['status']}', {lv['viewer_count']}, {lv['unique_viewers']}, '{lv['product_refs']}'::jsonb, '{lv['collab_refs']}'::jsonb, NOW(), NOW())
ON CONFLICT (livestream_id) DO UPDATE SET title = '{lv['title']}', status = '{lv['status']}';
""")

product_db_sql.append("COMMIT;")
print("  -> Đang thực thi seed vào product-service-product-db-1...")
full_prod_sql = "\n".join(product_db_sql)
subprocess.run(['docker', 'exec', '-i', 'product-service-product-db-1', 'psql', '-U', 'postgres', '-d', 'product_db'], input=full_prod_sql.encode('utf-8'), check=True)
print("  -> Seed product_db hoàn tất!")

# -------------------------------------------------------------
# 3. PROMOTION & AFFILIATE SERVICE (promotion_db)
# -------------------------------------------------------------
print("\n[3/6] Đang seed Hồ sơ KOL thời trang, lời mời hợp tác & voucher vào promotion_db...")

promo_sql = []
promo_sql.append("BEGIN;")

# 6 Collaborator Profiles
kol_profiles = [
    {
        'collab_id': 'collab-linh-01',
        'user_id': '0773dbd2-ef49-4aee-9089-231946d28279', # kolctv01
        'channel_name': 'Lê Thùy Linh (Mix & Match)',
        'platform': 'TIKTOK',
        'followers': 240000,
        'channel_url': 'https://tiktok.com/@linh.mixmatch'
    },
    {
        'collab_id': 'collab-shyn-02',
        'user_id': '33333333-1111-4000-8000-000000000001', # quynhanhshyn
        'channel_name': 'Quỳnh Anh Shyn Official',
        'platform': 'INSTAGRAM',
        'followers': 1200000,
        'channel_url': 'https://instagram.com/quynhanhshyn_'
    },
    {
        'collab_id': 'collab-nhi-03',
        'user_id': '33333333-1111-4000-8000-000000000002', # thaonhile
        'channel_name': 'Thảo Nhi Lê (Daphale Fashion)',
        'platform': 'INSTAGRAM',
        'followers': 850000,
        'channel_url': 'https://instagram.com/thaonhile'
    },
    {
        'collab_id': 'collab-chau-04',
        'user_id': '33333333-1111-4000-8000-000000000003', # chaubui
        'channel_name': 'Châu Bùi (Fashion & Lifestyle)',
        'platform': 'INSTAGRAM',
        'followers': 1500000,
        'channel_url': 'https://instagram.com/chaubui_'
    },
    {
        'collab_id': 'collab-anh-05',
        'user_id': '33333333-1111-4000-8000-000000000004', # phiphuonganh
        'channel_name': 'Phí Phương Anh (Gen Z Trends)',
        'platform': 'TIKTOK',
        'followers': 620000,
        'channel_url': 'https://tiktok.com/@phiphuonganhofficial'
    },
    {
        'collab_id': 'collab-fabo-06',
        'user_id': '33333333-1111-4000-8000-000000000005', # fabonguyen
        'channel_name': 'Fabo Nguyễn (Sneaker & Streetwear)',
        'platform': 'YOUTUBE',
        'followers': 780000,
        'channel_url': 'https://youtube.com/@fabonguyen'
    }
]

for kp in kol_profiles:
    app_data = json.dumps({
        'channelName': kp['channel_name'],
        'platform': kp['platform'],
        'followerCount': kp['followers'],
        'channelUrl': kp['channel_url'],
        'niche': 'Fashion & Clothing',
        'kycStatus': 'VERIFIED',
        'tier': 'GOLD' if kp['followers'] < 1000000 else 'DIAMOND'
    }, ensure_ascii=False).replace("'", "''")

    promo_sql.append(f"""
INSERT INTO collaborator_profiles (collaborator_id, user_id, approval_status, joined_at, policy_version, application_data, created_at, updated_at)
VALUES ('{kp['collab_id']}', '{kp['user_id']}', 'APPROVED', NOW() - INTERVAL '30 days', 'v1.0', '{app_data}'::jsonb, NOW(), NOW())
ON CONFLICT (collaborator_id) DO UPDATE SET
  application_data = '{app_data}'::jsonb,
  approval_status = 'APPROVED';
""")

# Partnership Invitations for Mộc Basics (Store 1)
invitations = [
    {
        'id': 'inv-moc-shyn-01',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'store_name': 'Mộc Basics - Thời Trang Bền Vững',
        'collab_id': 'collab-shyn-02',
        'collab_name': 'Quỳnh Anh Shyn Official',
        'product_id': 'prod-moc-04',
        'product_name': 'Áo Khoác Blazer Đũi Mộc Dáng Suông Hàn Quốc',
        'msg': 'Chào Quỳnh Anh, Mộc Basics rất ấn tượng với phong cách thanh lịch của bạn. Chúng mình gửi lời mời bạn trải nghiệm và chia sẻ mẫu Blazer Đũi Mộc mới nhất trong BST hè này nhé!',
        'status': 'ACCEPTED',
        'ref_link_id': 'ref-shyn-blazer',
        'token': 'MOC-SHYN-BLAZER'
    },
    {
        'id': 'inv-moc-chau-02',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'store_name': 'Mộc Basics - Thời Trang Bền Vững',
        'collab_id': 'collab-chau-04',
        'collab_name': 'Châu Bùi (Fashion & Lifestyle)',
        'product_id': 'prod-moc-01',
        'product_name': 'Áo Sơ Mi Linen Tay Dài Form Suông Mộc Basics',
        'msg': 'Mộc Basics trân trọng mời Châu Bùi hợp tác quảng bá dòng Áo Sơ Mi Linen Pháp sinh thái với mức hoa hồng 15% kèm mã độc quyền dành riêng cho fan của Châu.',
        'status': 'ACCEPTED',
        'ref_link_id': 'ref-chau-linen',
        'token': 'MOC-CHAU-LINEN'
    },
    {
        'id': 'inv-moc-linh-03',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'store_name': 'Mộc Basics - Thời Trang Bền Vững',
        'collab_id': 'collab-linh-01',
        'collab_name': 'Lê Thùy Linh (Mix & Match)',
        'product_id': 'prod-moc-03',
        'product_name': 'Quần Dài Linen Ống Suông Lưng Thun Tiện Lợi',
        'msg': 'Chào Thùy Linh, Mộc Basics muốn gửi tặng bạn mẫu Quần Dài Linen ống suông để Linh phối trong chuỗi video OOTD công sở tuần này nha!',
        'status': 'ACCEPTED',
        'ref_link_id': 'ref-linh-quan',
        'token': 'MOC-LINH-PANTS'
    },
    {
        'id': 'inv-moc-nhi-04',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'store_name': 'Mộc Basics - Thời Trang Bền Vững',
        'collab_id': 'collab-nhi-03',
        'collab_name': 'Thảo Nhi Lê (Daphale Fashion)',
        'product_id': 'prod-moc-02',
        'product_name': 'Áo Thun Cotton Compact 250gsm Thoáng Khí',
        'msg': 'Chào Thảo Nhi, Mộc Basics rất muốn kết nối với Nhi để giới thiệu dòng Cotton Compact basic cao cấp đến các bạn nữ yêu thích phong cách tối giản.',
        'status': 'PENDING',
        'ref_link_id': None,
        'token': None
    }
]

for inv in invitations:
    ref_id = f"'{inv['ref_link_id']}'" if inv['ref_link_id'] else "NULL"
    token_str = f"'{inv['token']}'" if inv['token'] else "NULL"
    resp_at = "NOW() - INTERVAL '5 days'" if inv['status'] == 'ACCEPTED' else "NULL"
    promo_sql.append(f"""
INSERT INTO partnership_invitations (id, store_id, store_name, collaborator_id, collaborator_name, product_id, product_name, message, status, referral_link_id, referral_token, created_at, responded_at)
VALUES ('{inv['id']}', '{inv['store_id']}', '{inv['store_name']}', '{inv['collab_id']}', '{inv['collab_name']}', '{inv['product_id']}', '{inv['product_name']}', '{inv['msg']}', '{inv['status']}', {ref_id}, {token_str}, NOW() - INTERVAL '7 days', {resp_at})
ON CONFLICT (id) DO UPDATE SET status = '{inv['status']}', message = '{inv['msg']}';
""")

# Referral Links
referral_links = [
    ('ref-shyn-blazer', 'collab-shyn-02', 'prod-moc-04', 'MOC-SHYN-BLAZER', 'Quỳnh Anh Shyn x Blazer Mộc Basics', 'INSTAGRAM'),
    ('ref-chau-linen', 'collab-chau-04', 'prod-moc-01', 'MOC-CHAU-LINEN', 'Châu Bùi x Sơ Mi Linen', 'INSTAGRAM'),
    ('ref-linh-quan', 'collab-linh-01', 'prod-moc-03', 'MOC-LINH-PANTS', 'Lê Thùy Linh x Quần Linen Suông', 'TIKTOK')
]

for rlid, cid, pid, tok, lbl, chn in referral_links:
    promo_sql.append(f"""
INSERT INTO referral_links (referral_link_id, collaborator_id, product_id, token, label, channel, status, created_at, expires_at)
VALUES ('{rlid}', '{cid}', '{pid}', '{tok}', '{lbl}', '{chn}', 'ACTIVE', NOW() - INTERVAL '15 days', NOW() + INTERVAL '90 days')
ON CONFLICT (referral_link_id) DO UPDATE SET token = '{tok}', status = 'ACTIVE';
""")

# Commission Policies
commission_policies = [
    ('pol-moc-store-default', '11111111-0000-0000-0000-000000000001', 'STORE', 'Chính sách hoa hồng thời trang Mộc Basics', 0.12, 1),
    ('pol-moc-blazer-promo', '11111111-0000-0000-0000-000000000001', 'PRODUCT', 'Hoa hồng đặc biệt Áo Blazer Đũi', 0.15, 2)
]

for pol_id, st_id, sc_type, p_name, rate, pri in commission_policies:
    promo_sql.append(f"""
INSERT INTO commission_policies (policy_id, store_id, scope_type, policy_name, commission_rate, priority, return_window_days, status, valid_from, version, created_at, updated_at)
VALUES ('{pol_id}', '{st_id}', '{sc_type}', '{p_name}', {rate}, {pri}, 7, 'ACTIVE', NOW() - INTERVAL '30 days', 0, NOW(), NOW())
ON CONFLICT (policy_id) DO UPDATE SET commission_rate = {rate}, status = 'ACTIVE';
""")

# Vouchers (Mã giảm giá thời trang)
vouchers_data = [
    {
        'id': 'vch-moc-10k',
        'code': 'MOCBASICS10',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'discount_type': 'PERCENTAGE',
        'value': 10.00,
        'cap': 50000,
        'min_subtotal': 250000,
        'scope_type': 'STORE'
    },
    {
        'id': 'vch-moc-ship',
        'code': 'FREESHIPMOC',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'discount_type': 'FIXED',
        'value': 30000.00,
        'cap': 30000,
        'min_subtotal': 300000,
        'scope_type': 'STORE'
    },
    {
        'id': 'vch-shyn-15',
        'code': 'SHYN15FASHION',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'discount_type': 'PERCENTAGE',
        'value': 15.00,
        'cap': 100000,
        'min_subtotal': 400000,
        'scope_type': 'STORE'
    }
]

for vc in vouchers_data:
    promo_sql.append(f"""
INSERT INTO vouchers (voucher_id, code, store_id, discount_type, value, cap, minimum_subtotal, scope_type, issuer_type, valid_from, valid_until, version, created_at, updated_at)
VALUES ('{vc['id']}', '{vc['code']}', '{vc['store_id']}', '{vc['discount_type']}', {vc['value']}, {vc['cap']}, {vc['min_subtotal']}, '{vc['scope_type']}', 'STORE', NOW() - INTERVAL '10 days', NOW() + INTERVAL '60 days', 0, NOW(), NOW())
ON CONFLICT (voucher_id) DO UPDATE SET code = '{vc['code']}', value = {vc['value']};
""")

promo_sql.append("COMMIT;")
print("  -> Đang thực thi seed vào scanms-promotion-db...")
full_promo_sql = "\n".join(promo_sql)
subprocess.run(['docker', 'exec', '-i', 'scanms-promotion-db', 'psql', '-U', 'postgres', '-d', 'promotion_db'], input=full_promo_sql.encode('utf-8'), check=True)
print("  -> Seed promotion_db hoàn tất!")

# -------------------------------------------------------------
# 4. ORDER SERVICE (order_db)
# -------------------------------------------------------------
print("\n[4/6] Đang seed Đơn hàng thời trang & Yêu cầu đổi trả vào order_db...")

order_sql = []
order_sql.append("BEGIN;")

orders_list = [
    {
        'order_id': 'ord-fashion-001',
        'cust_id': '44444444-1111-4000-8000-000000000001',
        'seller_order_id': 'so-fashion-001',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'order_item_id': 'oi-fashion-001',
        'prod_id': 'prod-moc-01',
        'prod_name': 'Áo Sơ Mi Linen Tay Dài Form Suông Mộc Basics',
        'var_id': 'var-prod-moc-01-m',
        'size': 'M',
        'qty': 1,
        'unit_price': 389000,
        'net_paid': 389000,
        'status': 'COMPLETED',
        'so_status': 'COMPLETED',
        'ship_id': 'ship-fashion-001',
        'tracking': 'GHN-FASHION-88801'
    },
    {
        'order_id': 'ord-fashion-002',
        'cust_id': '44444444-1111-4000-8000-000000000001',
        'seller_order_id': 'so-fashion-002',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'order_item_id': 'oi-fashion-002',
        'prod_id': 'prod-moc-02',
        'prod_name': 'Áo Thun Cotton Compact 250gsm Thoáng Khí',
        'var_id': 'var-prod-moc-02-l',
        'size': 'L',
        'qty': 2,
        'unit_price': 249000,
        'net_paid': 498000,
        'status': 'PAID',
        'so_status': 'SHIPPED',
        'ship_id': 'ship-fashion-002',
        'tracking': 'GHN-FASHION-88802'
    },
    {
        'order_id': 'ord-fashion-003',
        'cust_id': '44444444-1111-4000-8000-000000000001',
        'seller_order_id': 'so-fashion-003',
        'store_id': '11111111-0000-0000-0000-000000000001',
        'order_item_id': 'oi-fashion-003',
        'prod_id': 'prod-moc-04',
        'prod_name': 'Áo Khoác Blazer Đũi Mộc Dáng Suông Hàn Quốc',
        'var_id': 'var-prod-moc-04-l',
        'size': 'L',
        'qty': 1,
        'unit_price': 680000,
        'net_paid': 680000,
        'status': 'COMPLETED',
        'so_status': 'COMPLETED',
        'ship_id': 'ship-fashion-003',
        'tracking': 'GHN-FASHION-88803'
    }
]

for o in orders_list:
    shipping_snapshot = json.dumps({
        'recipientName': 'Trần Hoàng Mai Anh',
        'phone': '0933889900',
        'address': 'Toà nhà Landmark 81, 720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP.HCM'
    }, ensure_ascii=False).replace("'", "''")

    order_sql.append(f"""
INSERT INTO orders (order_id, customer_id, payable_vnd, status, currency, shipping_snapshot, created_at, updated_at)
VALUES ('{o['order_id']}', '{o['cust_id']}', {o['net_paid']}, '{o['status']}', 'VND', '{shipping_snapshot}'::jsonb, NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (order_id) DO UPDATE SET status = '{o['status']}';
""")

    totals_snapshot = json.dumps({'totalAmount': o['net_paid'], 'shippingFee': 0}).replace("'", "''")
    order_sql.append(f"""
INSERT INTO seller_orders (seller_order_id, order_id, store_id, status, totals_snapshot, version, created_at, updated_at)
VALUES ('{o['seller_order_id']}', '{o['order_id']}', '{o['store_id']}', '{o['so_status']}', '{totals_snapshot}'::jsonb, 0, NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (seller_order_id) DO UPDATE SET status = '{o['so_status']}';
""")

    product_snapshot = json.dumps({
        'name': o['prod_name'],
        'size': o['size'],
        'price': o['unit_price']
    }, ensure_ascii=False).replace("'", "''")

    order_sql.append(f"""
INSERT INTO order_items (order_item_id, seller_order_id, product_id, variant_id, selected_size, quantity, unit_price, gross_amount_vnd, net_paid_amount_vnd, product_snapshot, created_at)
VALUES ('{o['order_item_id']}', '{o['seller_order_id']}', '{o['prod_id']}', '{o['var_id']}', '{o['size']}', {o['qty']}, {o['unit_price']}, {o['unit_price'] * o['qty']}, {o['net_paid']}, '{product_snapshot}'::jsonb, NOW() - INTERVAL '3 days')
ON CONFLICT (order_item_id) DO NOTHING;
""")

    order_sql.append(f"""
INSERT INTO shipments (shipment_id, seller_order_id, carrier, tracking_code, status, environment, created_at, updated_at)
VALUES ('{o['ship_id']}', '{o['seller_order_id']}', 'GHN', '{o['tracking']}', 'DELIVERED', 'TEST', NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (shipment_id) DO UPDATE SET tracking_code = '{o['tracking']}';
""")

# Sample Return Requests (Yêu cầu trả hàng mẫu cho Shop)
return_requests = [
    {
        'ret_id': 'ret-fashion-001',
        'oi_id': 'oi-fashion-003',
        'reason': 'Kích cỡ không vừa vặn: Áo Blazer size L bị rộng vai, em muốn đổi sang size M của Mộc Basics.',
        'status': 'APPROVED',
        'refund_vnd': 0,
        'qty': 1,
        'evidences': json.dumps(['https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=600']).replace("'", "''"),
        'shop_response': json.dumps({'note': 'Shop đã duyệt đổi size M cho bạn, shipper sẽ giao tận nơi đồng kiểm nhé.', 'approvedAt': '2026-10-26T10:00:00Z'}, ensure_ascii=False).replace("'", "''")
    },
    {
        'ret_id': 'ret-fashion-002',
        'oi_id': 'oi-fashion-001',
        'reason': 'Muốn đổi màu sắc: Áo sơ mi Linen be mộc rất đẹp nhưng em hợp màu trắng hơn, xin phép đổi sang màu Trắng Ngà ạ.',
        'status': 'REQUESTED',
        'refund_vnd': 0,
        'qty': 1,
        'evidences': json.dumps(['https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=600']).replace("'", "''"),
        'shop_response': json.dumps({}, ensure_ascii=False).replace("'", "''")
    }
]

for rr in return_requests:
    order_sql.append(f"""
INSERT INTO return_requests (return_request_id, order_item_id, reason, status, quantity, refund_amount_vnd, evidence_refs, shop_response, requested_at)
VALUES ('{rr['ret_id']}', '{rr['oi_id']}', '{rr['reason']}', '{rr['status']}', {rr['qty']}, {rr['refund_vnd']}, '{rr['evidences']}'::jsonb, '{rr['shop_response']}'::jsonb, NOW() - INTERVAL '1 days')
ON CONFLICT (return_request_id) DO UPDATE SET reason = '{rr['reason']}', status = '{rr['status']}';
""")

order_sql.append("COMMIT;")
print("  -> Đang thực thi seed vào scanms-order-db...")
full_order_sql = "\n".join(order_sql)
subprocess.run(['docker', 'exec', '-i', 'scanms-order-db', 'psql', '-U', 'postgres', '-d', 'order_db'], input=full_order_sql.encode('utf-8'), check=True)
print("  -> Seed order_db hoàn tất!")

# -------------------------------------------------------------
# 5. PAYMENT SERVICE (payment_db)
# -------------------------------------------------------------
print("\n[5/6] Đang seed Ví điện tử & Số dư cho Shop & Creator vào payment_db...")

payment_sql = []
payment_sql.append("BEGIN;")

wallets_list = [
    # Store wallet for Mộc Basics
    ('wal-store-moc-basics', '11111111-0000-0000-0000-000000000001', 'STORE', 14850000, 2450000),
    # Creator wallet for Lê Thùy Linh (kolctv01)
    ('wal-kol-linh', 'collab-linh-01', 'COLLABORATOR', 2150000, 450000),
    # Customer wallet
    ('wal-cust-mai-anh', '44444444-1111-4000-8000-000000000001', 'CUSTOMER', 1000000, 0)
]

for wid, o_ref, o_type, avail, held in wallets_list:
    payment_sql.append(f"""
INSERT INTO wallets (wallet_id, owner_ref_id, owner_type, available_balance_vnd, held_balance_vnd, currency, status, version, created_at, updated_at)
VALUES ('{wid}', '{o_ref}', '{o_type}', {avail}, {held}, 'VND', 'ACTIVE', 0, NOW() - INTERVAL '30 days', NOW())
ON CONFLICT (wallet_id) DO UPDATE SET available_balance_vnd = {avail}, held_balance_vnd = {held};
""")

payment_sql.append("COMMIT;")
print("  -> Đang thực thi seed vào scanms-payment-db...")
full_payment_sql = "\n".join(payment_sql)
subprocess.run(['docker', 'exec', '-i', 'scanms-payment-db', 'psql', '-U', 'postgres', '-d', 'payment_db'], input=full_payment_sql.encode('utf-8'), check=True)
print("  -> Seed payment_db hoàn tất!")

# -------------------------------------------------------------
# 6. FLUSH REDIS CATALOG CACHE
# -------------------------------------------------------------
print("\n[6/6] Đang làm mới Redis cache...")
try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(2)
    s.connect(('127.0.0.1', 6379))
    s.sendall(b"*1\r\n$7\r\nFLUSHDB\r\n")
    reply = s.recv(1024)
    s.close()
    print("  -> Đã làm mới toàn bộ Redis cache thành công (", reply.decode('utf-8', errors='ignore').strip(), ")")
except Exception as e:
    print("  -> Lưu ý khi làm mới Redis cache:", e)

print("\n=== HOÀN TẤT SEED DỮ LIỆU THỜI TRANG THÀNH CÔNG ===")
