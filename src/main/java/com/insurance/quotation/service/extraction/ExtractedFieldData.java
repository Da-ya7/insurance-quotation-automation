// package com.insurance.quotation.service.extraction;

// public class ExtractedFieldData {

//     private String customerName;
//     private String email;
//     private String phone;
//     private String passportNumber;
//     private String originCountry;
//     private String destinationCountry;
//     private String travelStartDate;
//     private String travelEndDate;
//     private Integer travellerCount;
//     private String requiredCoverage;

//     public ExtractedFieldData() {
//     }

//     public String getCustomerName() {
//         return customerName;
//     }

//     public void setCustomerName(String customerName) {
//         this.customerName = customerName;
//     }

//     public String getEmail() {
//         return email;
//     }

//     public void setEmail(String email) {
//         this.email = email;
//     }

//     public String getPhone() {
//         return phone;
//     }

//     public void setPhone(String phone) {
//         this.phone = phone;
//     }

//     public String getPassportNumber() {
//         return passportNumber;
//     }

//     public void setPassportNumber(String passportNumber) {
//         this.passportNumber = passportNumber;
//     }

//     public String getOriginCountry() {
//         return originCountry;
//     }

//     public void setOriginCountry(String originCountry) {
//         this.originCountry = originCountry;
//     }

//     public String getDestinationCountry() {
//         return destinationCountry;
//     }

//     public void setDestinationCountry(String destinationCountry) {
//         this.destinationCountry = destinationCountry;
//     }

//     public String getTravelStartDate() {
//         return travelStartDate;
//     }

//     public void setTravelStartDate(String travelStartDate) {
//         this.travelStartDate = travelStartDate;
//     }

//     public String getTravelEndDate() {
//         return travelEndDate;
//     }

//     public void setTravelEndDate(String travelEndDate) {
//         this.travelEndDate = travelEndDate;
//     }

//     public Integer getTravellerCount() {
//         return travellerCount;
//     }

//     public void setTravellerCount(Integer travellerCount) {
//         this.travellerCount = travellerCount;
//     }

//     public String getRequiredCoverage() {
//         return requiredCoverage;
//     }

//     public void setRequiredCoverage(String requiredCoverage) {
//         this.requiredCoverage = requiredCoverage;
//     }

//     @Override
//     public String toString() {
//         return "ExtractedFieldData{" +
//                 "customerName='" + customerName + '\'' +
//                 ", email='" + email + '\'' +
//                 ", phone='" + phone + '\'' +
//                 ", passportNumber='" + passportNumber + '\'' +
//                 ", originCountry='" + originCountry + '\'' +
//                 ", destinationCountry='" + destinationCountry + '\'' +
//                 ", travelStartDate='" + travelStartDate + '\'' +
//                 ", travelEndDate='" + travelEndDate + '\'' +
//                 ", travellerCount=" + travellerCount +
//                 ", requiredCoverage='" + requiredCoverage + '\'' +
//                 '}';
//     }
// }

package com.insurance.quotation.service.extraction;

public class ExtractedFieldData {

    private String name;
    private String email;
    private String phone;
    private String passportNumber;
    private String originCountry;
    private String destinationCountry;
    private String travelStartDate;
    private String travelEndDate;
    private String travellerCount;
    private String requiredCoverage;

    public ExtractedFieldData() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }

    public String getTravelStartDate() {
        return travelStartDate;
    }

    public void setTravelStartDate(String travelStartDate) {
        this.travelStartDate = travelStartDate;
    }

    public String getTravelEndDate() {
        return travelEndDate;
    }

    public void setTravelEndDate(String travelEndDate) {
        this.travelEndDate = travelEndDate;
    }

    public String getTravellerCount() {
        return travellerCount;
    }

    public void setTravellerCount(String travellerCount) {
        this.travellerCount = travellerCount;
    }

    public String getRequiredCoverage() {
        return requiredCoverage;
    }

    public void setRequiredCoverage(String requiredCoverage) {
        this.requiredCoverage = requiredCoverage;
    }

    @Override
    public String toString() {
        return "ExtractedFieldData{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", passportNumber='" + passportNumber + '\'' +
                ", originCountry='" + originCountry + '\'' +
                ", destinationCountry='" + destinationCountry + '\'' +
                ", travelStartDate='" + travelStartDate + '\'' +
                ", travelEndDate='" + travelEndDate + '\'' +
                ", travellerCount='" + travellerCount + '\'' +
                ", requiredCoverage='" + requiredCoverage + '\'' +
                '}';
    }
}