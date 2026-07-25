# Deploy Move_home lên AWS EC2

## Kiến trúc đề xuất

- Một EC2 Amazon Linux 2023 chạy Docker Compose.
- Nginx phục vụ frontend tĩnh và reverse proxy `/api`, `/ws` tới Spring Boot.
- PostgreSQL tiếp tục dùng Neon; không mở database ra Internet từ EC2.
- Cloudinary, VNPay Sandbox và Gmail SMTP tiếp tục là dịch vụ ngoài.
- Domain trỏ vào Elastic IP. Bắt buộc bật HTTPS trước khi cho người dùng đăng nhập.

Phương án này phù hợp demo/đồ án và một instance. Không phải kiến trúc high availability.

## 1. Tạo EC2

1. Chọn Region `ap-southeast-1` (Singapore), Amazon Linux 2023, kiến trúc x86_64.
2. Với demo nhỏ, bắt đầu bằng `t3.small` (2 GiB RAM). `t3.micro` có thể thiếu RAM khi Maven build.
3. Security Group inbound:
   - TCP 22: chỉ IP của thành viên quản trị.
   - TCP 80: `0.0.0.0/0` và `::/0`.
   - TCP 443: `0.0.0.0/0` và `::/0`.
   - Không mở 8080 hoặc 5432.
4. Gán Elastic IP để địa chỉ không đổi, rồi tạo DNS A record tới Elastic IP.

## 2. Cài Docker trên EC2

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

Đăng xuất SSH rồi đăng nhập lại. Cài Docker Compose plugin theo hướng dẫn Docker chính thức nếu lệnh `docker compose version` chưa có.

## 3. Đưa mã nguồn và secrets lên máy

```bash
git clone YOUR_REPOSITORY_URL move-home
cd move-home
cp .env.aws.example .env
chmod 600 .env
```

Điền toàn bộ giá trị trong `.env`. Không commit file này. `YOUR_DOMAIN` phải là domain HTTPS thật, không có dấu `/` cuối.

## 4. Chạy thử qua HTTP

```bash
docker compose -f docker-compose.aws.yml build
docker compose -f docker-compose.aws.yml up -d
docker compose -f docker-compose.aws.yml ps
docker compose -f docker-compose.aws.yml logs --tail=200 backend
```

Kiểm tra `http://ELASTIC_IP/frontend/index.html`. HTTP chỉ dùng để smoke test, không dùng đăng nhập hoặc nhập dữ liệu thật.

## 5. Bật HTTPS

Dùng domain + chứng chỉ TLS trước khi mở cho người dùng. Có hai lựa chọn:

1. Application Load Balancer + AWS Certificate Manager, listener 443 chuyển tiếp vào port 80 của EC2. Đây là lựa chọn AWS-native, chứng chỉ tự gia hạn nhưng có thêm chi phí ALB.
2. Cài Certbot trên EC2 và terminate TLS tại Nginx. Rẻ hơn nhưng nhóm phải tự vận hành gia hạn và mount certificate vào container.

Sau khi HTTPS hoạt động, đổi Security Group để port 80 chỉ redirect sang 443, xác nhận các URL VNPay/IPN và ba URL frontend trong `.env` đều dùng `https://`.

## 6. Kiểm tra sau deploy

```bash
curl -I https://YOUR_DOMAIN/frontend/index.html
curl -i -X POST https://YOUR_DOMAIN/api/public/quote-estimate \
  -H 'Content-Type: application/json' \
  -d '{}'
docker compose -f docker-compose.aws.yml logs --tail=200 backend web
```

Sau đó kiểm tra lần lượt: đăng ký/xác thực email, đăng nhập từng role, upload Cloudinary, chat WebSocket, tạo URL VNPay và IPN callback. Flyway tự chạy khi backend khởi động; luôn backup database trước khi đưa migration mới lên production.

## Cập nhật phiên bản

```bash
git pull --ff-only
docker compose -f docker-compose.aws.yml build backend
docker compose -f docker-compose.aws.yml up -d
docker compose -f docker-compose.aws.yml logs --tail=200 backend
```

Nếu backend không khởi động, giữ container/log cũ để điều tra; không xóa volume hay sửa trực tiếp schema database.
