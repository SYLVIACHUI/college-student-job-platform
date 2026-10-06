package cn.campus.jobs.service;

import cn.campus.jobs.mapper.CompanyMapper;

import cn.campus.jobs.mapper.UserRepository;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {
    private final CompanyMapper mapper;
    private final UserRepository users;
    private final ProfileService profiles;
    private final JobService jobs;
    public CompanyService(UserRepository users,ProfileService profiles,JobService jobs,CompanyMapper mapper){ this.mapper=mapper;this.users=users;this.profiles=profiles;this.jobs=jobs;}

    private Map<String,Object> publisher(String id){
        var account=users.findAccountById(id);
        if(account==null || !"PUBLISHER".equals(account.toMap().get("role")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"企业不存在");
        return account.toMap();
    }
    public String owner(Map<String,Object> current){
        if(!"PUBLISHER".equals(current.get("role")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅企业账号可以编辑企业主页");
        return current.get("id").toString();
    }
    public Map<String,Object> home(String id){
        var user=publisher(id);
        var profile=profiles.homepage(id);
        var rows=mapper.profile(id);
        var result=new LinkedHashMap<String,Object>();
        result.put("id",id);
        result.put("name",profile.get("organization"));
        result.put("verification_status",profile.get("verification_status"));
        result.put("joined_at",user.get("created_at"));
        result.put("introduction",rows.isEmpty()?"":rows.get(0).get("introduction"));
        // A publisher account is the current recruiter. Never merge accounts by organization name.
        result.put("recruiters",List.of(profile));
        var photos=mapper.photos(id);
        for(var photo:photos)photo.put("url","/api/companies/"+id+"/photos/"+photo.get("id"));
        result.put("photos",photos);
        return result;
    }
    public Object jobs(String id,int page){publisher(id);return jobs.companyJobs(id,page);}
    @Transactional
    public void edit(String id,String introduction){
        users.lockById(id);
        if(mapper.profileCount(id)==0)
            mapper.insertProfile(id,introduction.trim());
        else mapper.updateProfile(introduction.trim(),id);
    }
    @Transactional
    public void upload(String id,MultipartFile file){
        byte[] image=reencode(file);
        users.lockById(id);
        if(mapper.photoCount(id)>=6)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"最多上传6张企业照片，请先删除旧照片");
        mapper.insertPhoto(UUID.randomUUID().toString(),id,image);
    }
    @Transactional
    public void delete(String id,String photoId){
        users.lockById(id);
        if(mapper.deletePhoto(photoId,id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"照片不存在或无权删除");
    }
    public byte[] photo(String id,String photoId){
        var rows=mapper.photo(id,photoId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"照片不存在");
        return rows.get(0);
    }
    private byte[] reencode(MultipartFile file){
        if(file.isEmpty() || file.getSize()>2*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片大小需在2MB以内");
        try(var stream=ImageIO.createImageInputStream(file.getInputStream())){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IOException();
            var reader=readers.next();
            try{
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw new IOException();
                reader.setInput(stream);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>4096||h>4096)throw new IOException();
                double scale=Math.min(1,1600.0/Math.max(w,h));
                BufferedImage output=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_RGB);
                var graphics=output.createGraphics();
                try{
                    graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,output.getWidth(),output.getHeight());
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(reader.read(0),0,0,output.getWidth(),output.getHeight(),null);
                }finally{graphics.dispose();}
                var bytes=new ByteArrayOutputStream();ImageIO.write(output,"jpeg",bytes);return bytes.toByteArray();
            }finally{reader.dispose();}
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请上传有效的PNG或JPEG照片，宽高不超过4096像素");}
    }
}
